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

@file:Suppress("UnstableApiUsage")

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
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.getContainingUClass
import java.util.EnumSet

class NoHardcodedPetalPixelParamDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(ULiteralExpression::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitLiteralExpression(node: ULiteralExpression) {
            val literal = node.value as? String ?: return
            val containingClass = node.getContainingUClass()?.qualifiedName.orEmpty()
            if (containingClass.startsWith(PIXEL_API_CLASS)) return

            if (literal == PETAL_KEY) {
                context.report(NO_HARDCODED_PETAL_PIXEL_PARAM, node, context.getLocation(node), KEY_MESSAGE)
                return
            }

            if (literal in PETAL_VALUES && isPetalValue(node, containingClass)) {
                context.report(NO_HARDCODED_PETAL_PIXEL_PARAM, node, context.getLocation(node), VALUE_MESSAGE)
            }
        }
    }

    /**
     * A value such as "randomize" only belongs to the petal parameter when it sits next to the petal
     * key — `PETAL to "randomize"`, `put(PETAL, "randomize")` — or when it is declared in a
     * feature-local petal constants holder, which is the re-declaration this rule exists to stop.
     */
    private fun isPetalValue(node: ULiteralExpression, containingClass: String): Boolean =
        isPairedWithPetalKey(node) || containingClass.substringAfterLast('.').contains(PETAL_HOLDER, ignoreCase = true)

    private fun isPairedWithPetalKey(node: ULiteralExpression): Boolean {
        // sourcePsi of a Kotlin string literal is the template entry, so step out to the expression first.
        val psi = node.sourcePsi ?: return false
        val literal = psi as? KtStringTemplateExpression ?: psi.parent as? KtStringTemplateExpression ?: return false

        (literal.parent as? KtBinaryExpression)?.let { pair ->
            return pair.left?.text?.contains(PETAL_CONSTANT) == true
        }

        val argument = literal.parent as? KtValueArgument ?: return false
        val call = argument.parent?.parent as? KtCallExpression ?: return false
        val precedingArgument = call.valueArguments.indexOf(argument).takeIf { it > 0 }
            ?.let { call.valueArguments[it - 1] } ?: return false
        return precedingArgument.text.contains(PETAL_CONSTANT)
    }

    companion object {
        const val ERROR_ID = "NoHardcodedPetalPixelParam"
        const val ERROR_DESCRIPTION = "Hardcoded petal pixel parameter"
        const val KEY_MESSAGE = "Use Pixel.PixelParameter.PETAL instead of hardcoding the petal parameter key"
        const val VALUE_MESSAGE = "Use Pixel.PixelValues.PETAL_RANDOMIZE or Pixel.PixelValues.PETAL_KANON " +
            "instead of hardcoding the petal parameter value"

        private const val PETAL_KEY = "petal"
        private const val PETAL_CONSTANT = "PETAL"
        private const val PETAL_HOLDER = "petal"
        private const val PIXEL_API_CLASS = "com.duckduckgo.app.statistics.pixels.Pixel"
        private val PETAL_VALUES = setOf("randomize", "kanon", "true")

        val NO_HARDCODED_PETAL_PIXEL_PARAM = Issue.create(
            ERROR_ID,
            ERROR_DESCRIPTION,
            "The petal parameter routes a pixel through a backend processing pipeline, so its key and values are " +
                "shared constants in Pixel.PixelParameter and Pixel.PixelValues. Re-declaring or hardcoding them " +
                "lets the two sides drift apart. Note that PETAL_KANON is only for pixels where k-anonymity was " +
                "explicitly requested in privacy triage.",
            Category.CORRECTNESS, 10, ERROR,
            Implementation(NoHardcodedPetalPixelParamDetector::class.java, EnumSet.of(JAVA_FILE, TEST_SOURCES)),
        )
    }
}
