/*
 * Copyright (c) 2023 DuckDuckGo
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
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.skipParenthesizedExprDown
import java.util.*

@Suppress("UnstableApiUsage")
class NoRobolectricTestRunnerDetector : Detector(), SourceCodeScanner {
    override fun getApplicableUastTypes() = listOf(UAnnotation::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = NoInternalImportHandler(context)

    internal class NoInternalImportHandler(private val context: JavaContext) : UElementHandler() {
        override fun visitAnnotation(node: UAnnotation) {
            if (node.qualifiedName?.contains("RunWith") == true) {
                val attributes = node.attributeValues
                if (attributes.size == 1) {
                    val attribute = attributes[0]
                    val value = attribute.expression.skipParenthesizedExprDown()
                    val klass = value?.asSourceString() ?: return
                    if (!klass.contains("Parameterized") && klass.contains("RobolectricTestRunner")) {
                        context.report(
                            NO_ROBOLECTRIC_TEST_RUNNER_ISSUE,
                            node,
                            context.getNameLocation(node),
                            "The RobolectricTestRunner parameter must not be used in the RunWith annotation."
                        )
                    }
                }
            }
        }
    }

    companion object {
        val NO_ROBOLECTRIC_TEST_RUNNER_ISSUE = Issue.create("NoRobolectricTestRunnerAnnotation",
            "The RobolectricTestRunner parameter must not be used in the RunWith annotation",
            """
                The @RunWith(RobolectricTestRunner::class) annotation must not be used.               
                Use @RunWith(AndroidJUnit4::class) instead.
            """.trimIndent(),
            Category.CORRECTNESS, 10, ERROR,
            Implementation(NoRobolectricTestRunnerDetector::class.java, EnumSet.of(JAVA_FILE, TEST_SOURCES))
        )
    }
}
