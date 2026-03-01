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
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UImportStatement
import java.util.EnumSet

/**
 * Detector that prevents direct usage of [MetricsPixelExtension] and [MetricsPixelExtensionProvider].
 * These are internal implementation details — callers should use [MetricsPixel.send()] instead.
 */
@Suppress("UnstableApiUsage")
class NoMetricsPixelExtensionUsageDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UImportStatement::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return NoMetricsPixelExtensionImportHandler(context)
    }

    internal class NoMetricsPixelExtensionImportHandler(private val context: JavaContext) : UElementHandler() {
        override fun visitImportStatement(node: UImportStatement) {
            val importPath = node.importReference?.asSourceString() ?: return

            if (!isBannedImport(importPath)) return

            // feature-toggles-impl is the only module allowed to reference these internals
            val packageName = context.uastFile?.packageName.orEmpty()
            if (packageName.startsWith("com.duckduckgo.feature.toggles.impl")) return

            val typeName = importPath.substringAfterLast('.')
            context.report(
                NO_METRICS_PIXEL_EXTENSION_USAGE,
                node,
                context.getLocation(node),
                "`$typeName` is an internal implementation detail. Use `MetricsPixel.send()` instead.",
            )
        }

        private fun isBannedImport(importPath: String): Boolean {
            return importPath.endsWith("MetricsPixelExtension") || importPath.endsWith("MetricsPixelExtensionProvider")
        }
    }

    companion object {
        val NO_METRICS_PIXEL_EXTENSION_USAGE = Issue.create(
            "NoMetricsPixelExtensionUsage",
            "Do not use MetricsPixelExtension or MetricsPixelExtensionProvider directly",
            """
                `MetricsPixelExtension` and `MetricsPixelExtensionProvider` are internal
                implementation details and must not be used outside `feature-toggles-impl`.

                Use the public API instead:

                    myMetricsPixel.send()
            """.trimIndent(),
            Category.CORRECTNESS,
            10,
            Severity.ERROR,
            Implementation(
                NoMetricsPixelExtensionUsageDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
            ),
        )
    }
}
