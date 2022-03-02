// Copyright 2015 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.rules.objc;

import static com.google.devtools.build.lib.packages.Attribute.attr;
import static com.google.devtools.build.lib.packages.BuildType.LABEL_LIST;
import static com.google.devtools.build.lib.packages.Type.STRING_LIST;

import com.google.devtools.build.lib.analysis.BaseRuleClasses;
import com.google.devtools.build.lib.analysis.RuleDefinition;
import com.google.devtools.build.lib.analysis.RuleDefinitionEnvironment;
import com.google.devtools.build.lib.analysis.config.ToolchainTypeRequirement;
import com.google.devtools.build.lib.packages.Attribute.ValidityPredicate;
import com.google.devtools.build.lib.packages.Rule;
import com.google.devtools.build.lib.packages.RuleClass;
import com.google.devtools.build.lib.packages.RuleClass.Builder.RuleClassType;
import com.google.devtools.build.lib.packages.RuleClass.ToolchainTransitionMode;
import com.google.devtools.build.lib.rules.apple.AppleConfiguration;
import com.google.devtools.build.lib.rules.cpp.CppConfiguration;
import com.google.devtools.build.lib.rules.cpp.CppRuleClasses;

/**
 * Abstract rule definition for j2objc_library.
 */
public class J2ObjcLibraryBaseRule implements RuleDefinition {
  private final J2ObjcAspect j2ObjcAspect;

  public J2ObjcLibraryBaseRule(J2ObjcAspect j2ObjcAspect) {
    this.j2ObjcAspect = j2ObjcAspect;
  }

  @Override
  public RuleClass build(RuleClass.Builder builder, RuleDefinitionEnvironment env) {
    // TODO(rduan): Add support for package prefixes.
    return builder
        .requiresConfigurationFragments(
            AppleConfiguration.class,
            CppConfiguration.class,
            J2ObjcConfiguration.class,
            ObjcConfiguration.class)
        /* <!-- #BLAZE_RULE($j2objc_library_base_rule).ATTRIBUTE(deps) -->
        A list of <code>j2objc_library</code>, <code>java_library</code>,
        <code>java_import</code> and <code>java_proto_library</code> targets that contain
        Java files to be transpiled to Objective-C.
        <p>All <code>java_library</code> and <code>java_import</code> targets that can be reached
        transitively through <code>exports</code>, <code>deps</code> and <code>runtime_deps</code>
        will be translated and compiled. Currently there is no support for files generated by Java
        annotation processing or <code>java_import</code> targets with no <code>srcjar</code>
        specified.
        </p>
        <p>The J2ObjC translation works differently depending on the type of source Java source
        files included in the transitive closure. For each .java source files included in
        <code>srcs</code> of <code>java_library</code>, a corresponding .h and .m source file
        will be generated. For each source jar included in <code>srcs</code> of
        <code>java_library</code> or <code>srcjar</code> of <code>java_import</code>, a
        corresponding .h and .m source file will be generated with all the code for that jar.
        </p>
        <p>Users can import the J2ObjC-generated header files in their code. The import paths for
        these files are the root-relative path of the original Java artifacts. For example,
        <code>//some/package/foo.java</code> has an import path of <code>some/package/foo.h</code>
        and <code>//some/package/bar.srcjar</code> has <code>some/package/bar.h</code
        </p>
        <p>
        If proto_library rules are in the transitive closure of this rule, J2ObjC protos will also
        be generated, compiled and linked-in at the binary level. For proto
        <code>//some/proto/foo.proto</code>, users can reference the generated code using import
        path <code>some/proto/foo.j2objc.pb.h</code>.
        </p>
        <!-- #END_BLAZE_RULE.ATTRIBUTE -->*/
        .add(
            attr("deps", LABEL_LIST)
                .aspect(j2ObjcAspect)
                .direct_compile_time_input()
                .allowedRuleClasses(
                    "j2objc_library", "java_library", "java_import", "java_proto_library")
                .allowedFileTypes())
        /* <!-- #BLAZE_RULE($j2objc_library_base_rule).ATTRIBUTE(entry_classes) -->
        The list of Java classes whose translated ObjC counterparts will be referenced directly
        by user ObjC code. This attibute is required if flag <code>--j2objc_dead_code_removal
        </code> is on. The Java classes should be specified in their canonical names as defined by
        <a href="http://docs.oracle.com/javase/specs/jls/se8/html/jls-6.html#jls-6.7">the Java
        Language Specification.</a>
        When flag <code>--j2objc_dead_code_removal</code> is specified, the list of entry classes
        will be collected transitively and used as entry points to perform dead code analysis.
        Unused classes will then be removed from the final ObjC app bundle.
        <!-- #END_BLAZE_RULE.ATTRIBUTE -->*/
        .add(attr("entry_classes", STRING_LIST))
        /* <!-- #BLAZE_RULE($j2objc_library_base_rule).ATTRIBUTE(jre_deps) -->
        The list of additional JRE emulation libraries required by all Java code translated by this
        <code>j2objc_library</code> rule. Only core JRE functionality is linked by default.
        <!-- #END_BLAZE_RULE.ATTRIBUTE -->*/
        .add(
            attr("jre_deps", LABEL_LIST)
                .direct_compile_time_input()
                .allowedRuleClasses("objc_library")
                .allowedFileTypes()
                .validityPredicate(
                    new ValidityPredicate() {
                      @Override
                      public String checkValid(Rule from, Rule to) {
                        if (!to.getRuleTags().contains("j2objc_jre_lib")) {
                          return "Only J2ObjC JRE libraries are allowed";
                        }
                        return null;
                      }
                    }))
        // TODO(https://github.com/bazelbuild/bazel/issues/14727): Evaluate whether this can be
        // optional.
        .addToolchainTypes(
            ToolchainTypeRequirement.builder(CppRuleClasses.ccToolchainTypeAttribute(env))
                .mandatory(true)
                .build())
        .useToolchainTransition(ToolchainTransitionMode.ENABLED)
        .build();
  }

  @Override
  public Metadata getMetadata() {
    return RuleDefinition.Metadata.builder()
        .name("$j2objc_library_base_rule")
        .type(RuleClassType.ABSTRACT)
        .ancestors(BaseRuleClasses.NativeBuildRule.class)
        .build();
  }
}

/*<!-- #BLAZE_RULE (NAME = j2objc_library, TYPE = LIBRARY, FAMILY = Objective-C) -->

<p> This rule uses <a href="https://github.com/google/j2objc">J2ObjC</a> to translate Java source
files to Objective-C, which then can be used used as dependencies of objc_library and objc_binary
rules. Detailed information about J2ObjC itself can be found at  <a href="http://j2objc.org">the
J2ObjC site</a>
</p>
<p>Custom J2ObjC transpilation flags can be specified using the build flag
<code>--j2objc_translation_flags</code> in the command line.
</p>
<p>Please note that the translated files included in a j2objc_library target will be
compiled using the default compilation configuration, same configuration as for the sources of an
objc_library rule with no compilation options specified in attributes.
</p>
<p>Plus, generated code is de-duplicated at target level, not source level. If you have two
different Java targets that include the same Java source files, you may see a duplicate symbol error
at link time. The correct way to resolve this issue is to move the shared Java source files into a
separate common target that can be depended upon.
</p>


<!-- #END_BLAZE_RULE -->*/
