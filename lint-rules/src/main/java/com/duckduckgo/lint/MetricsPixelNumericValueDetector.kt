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

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import java.util.EnumSet

@Suppress("UnstableApiUsage")
class MetricsPixelNumericValueDetector : Detector(), SourceCodeScanner {

    override fun getApplicableConstructorTypes() =
        listOf("com.duckduckgo.feature.toggles.api.MetricsPixel")

    override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
        val typeArg = findArgument(node, constructor, "type") ?: return
        val typeText = typeArg.sourcePsi?.text ?: return
        if (!typeText.contains("COUNT_WHEN_IN_WINDOW") && !typeText.contains("COUNT_ALWAYS")) return

        val valueArg = findArgument(node, constructor, "value") ?: return
        val valueStr = valueArg.evaluate() as? String ?: return

        if (valueStr.toIntOrNull() == null) {
            context.report(
                NUMERIC_VALUE_REQUIRED,
                valueArg,
                context.getLocation(valueArg),
                "`value` must be a valid integer string for COUNT_WHEN_IN_WINDOW/COUNT_ALWAYS pixel types (got \"$valueStr\")",
            )
        }
    }

    private fun findArgument(node: UCallExpression, constructor: PsiMethod, paramName: String): UExpression? {
        val params = constructor.parameterList.parameters
        val idx = params.indexOfFirst { it.name == paramName }
        return if (idx >= 0 && idx < node.valueArguments.size) node.valueArguments[idx] else null
    }

    companion object {
        val NUMERIC_VALUE_REQUIRED = Issue.create(
            "MetricsPixelNumericValue",
            "MetricsPixel value must be a numeric string for counting types",
            "When MetricType is COUNT_WHEN_IN_WINDOW or COUNT_ALWAYS, the `value` field is " +
                "called with `.toInt()` at runtime. Providing a non-integer string will crash.",
            Category.CORRECTNESS,
            10,
            Severity.ERROR,
            Implementation(
                MetricsPixelNumericValueDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
            ),
        )
    }
}
