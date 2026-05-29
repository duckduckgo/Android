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
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.uast.UMethod
import java.util.EnumSet

@Suppress("UnstableApiUsage")
class MissingExplicitReturnTypeOnProvidesBindsDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = Handler(context)

    internal class Handler(private val context: JavaContext) : UElementHandler() {
        override fun visitMethod(node: UMethod) {
            val isProvidesOrBinds = node.uAnnotations.any { it.qualifiedName in DAGGER_BINDING_ANNOTATIONS }
            if (!isProvidesOrBinds) return

            // Java methods always have an explicit return type. Only Kotlin expression-body
            // functions can omit it via inference, so only inspect Kotlin sources.
            val ktFunction = node.sourcePsi as? KtNamedFunction ?: return
            if (ktFunction.typeReference == null) {
                context.report(
                    MISSING_EXPLICIT_RETURN_TYPE,
                    node,
                    context.getNameLocation(node),
                    "@Provides/@Binds methods must declare an explicit return type for Metro compatibility. " +
                        "Change 'fun foo(...) = expr' to 'fun foo(...): ReturnType = expr'.",
                )
            }
        }
    }

    companion object {
        private val DAGGER_BINDING_ANNOTATIONS = setOf("dagger.Provides", "dagger.Binds")

        val MISSING_EXPLICIT_RETURN_TYPE = Issue.create(
            "MissingExplicitReturnTypeOnProvidesBinds",
            "@Provides/@Binds method is missing an explicit return type",
            """
                Dagger @Provides and @Binds methods must declare an explicit return type. Metro DI
                cannot rely on Kotlin return-type inference: it needs the declared type to wire the
                binding into the dependency graph.

                Change 'fun foo(...) = expr' to 'fun foo(...): ReturnType = expr'.
            """.trimIndent(),
            Category.CORRECTNESS,
            10,
            ERROR,
            Implementation(
                MissingExplicitReturnTypeOnProvidesBindsDetector::class.java,
                EnumSet.of(JAVA_FILE, TEST_SOURCES),
            ),
        )
    }
}
