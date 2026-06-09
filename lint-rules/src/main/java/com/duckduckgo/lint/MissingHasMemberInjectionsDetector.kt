/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope.JAVA_FILE
import com.android.tools.lint.detector.api.Scope.TEST_SOURCES
import com.android.tools.lint.detector.api.Severity.ERROR
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.UClass
import java.util.EnumSet

@Suppress("UnstableApiUsage")
class MissingHasMemberInjectionsDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = Handler(context)

    internal class Handler(private val context: JavaContext) : UElementHandler() {
        override fun visitClass(node: UClass) {
            // @HasMemberInjections is only required on non-final classes
            if (node.hasModifierProperty(PsiModifier.FINAL)) return

            // Member injection means @Inject on a field/property declaration (not a constructor param).
            // UAST exposes Kotlin properties as fields; constructor `val`/`var` parameters are not
            // included unless explicitly redeclared in the class body, so checking field-level
            // @Inject is sufficient to disambiguate constructor injection from member injection.
            val hasMemberInjection = node.fields.any { field ->
                field.uAnnotations.any { it.qualifiedName == JAVAX_INJECT }
            }
            if (!hasMemberInjection) return

            val hasMarker = node.uAnnotations.any { it.qualifiedName == METRO_HAS_MEMBER_INJECTIONS }
            if (!hasMarker) {
                context.report(
                    MISSING_HAS_MEMBER_INJECTIONS,
                    node,
                    context.getNameLocation(node),
                    "Class has @Inject members but is missing @HasMemberInjections. Metro requires this " +
                        "marker on every class with field/property injection. Add @HasMemberInjections from " +
                        "dev.zacsweers.metro.",
                )
            }
        }
    }

    companion object {
        private const val JAVAX_INJECT = "javax.inject.Inject"
        private const val METRO_HAS_MEMBER_INJECTIONS = "dev.zacsweers.metro.HasMemberInjections"

        val MISSING_HAS_MEMBER_INJECTIONS = Issue.create(
            "MissingHasMemberInjections",
            "Class with @Inject members is missing @HasMemberInjections",
            """
                Any class that uses field or property injection (@Inject on a member, as opposed to
                @Inject constructor) must also be annotated with @HasMemberInjections from
                dev.zacsweers.metro. Without this marker, Metro will not generate the member-injection
                plumbing and the class will fail at runtime under Metro DI.

                The marker must be declared on every class with @Inject members directly — including
                concrete subclasses of bases that already declare it.
            """.trimIndent(),
            Category.CORRECTNESS,
            10,
            ERROR,
            Implementation(MissingHasMemberInjectionsDetector::class.java, EnumSet.of(JAVA_FILE, TEST_SOURCES)),
        )
    }
}
