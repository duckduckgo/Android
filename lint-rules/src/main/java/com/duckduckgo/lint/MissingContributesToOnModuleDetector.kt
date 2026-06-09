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
import org.jetbrains.uast.UClass
import java.util.EnumSet

@Suppress("UnstableApiUsage")
class MissingContributesToOnModuleDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = Handler(context)

    internal class Handler(private val context: JavaContext) : UElementHandler() {
        override fun visitClass(node: UClass) {
            val moduleAnnotation = node.uAnnotations.firstOrNull { it.qualifiedName == DAGGER_MODULE } ?: return

            val hasContributesTo = node.uAnnotations.any { it.qualifiedName == ANVIL_CONTRIBUTES_TO }
            if (!hasContributesTo) {
                context.report(
                    MISSING_CONTRIBUTES_TO_ON_MODULE,
                    moduleAnnotation,
                    context.getNameLocation(moduleAnnotation),
                    "Dagger @Module must be annotated with @ContributesTo to be discovered by Metro DI. " +
                        "Add @ContributesTo(AppScope::class) — or the appropriate scope (ActivityScope, FragmentScope, " +
                        "VpnScope, etc.) — alongside @Module.",
                )
            }
        }
    }

    companion object {
        private const val DAGGER_MODULE = "dagger.Module"
        private const val ANVIL_CONTRIBUTES_TO = "com.squareup.anvil.annotations.ContributesTo"

        val MISSING_CONTRIBUTES_TO_ON_MODULE = Issue.create(
            "MissingContributesToOnModule",
            "Dagger @Module is missing @ContributesTo",
            """
                Every Dagger @Module must also be annotated with @ContributesTo so that Metro DI can
                auto-discover it. Add @ContributesTo(AppScope::class), or the appropriate scope
                (ActivityScope, FragmentScope, VpnScope, etc.), alongside @Module.

                This is required for the Anvil → Metro migration: Metro relies on @ContributesTo to
                merge modules into the dependency graph.
            """.trimIndent(),
            Category.CORRECTNESS,
            10,
            ERROR,
            Implementation(MissingContributesToOnModuleDetector::class.java, EnumSet.of(JAVA_FILE, TEST_SOURCES)),
        )
    }
}
