/*
 * Copyright (c) 2025 DuckDuckGo
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
import java.util.*

/**
 * Detector that prevents modules from importing classes from :*-impl modules, unless
 * the source file itself is also in an impl module or an internal module (internal flavor).
 * This enforces API/impl separation throughout the codebase.
 */
@Suppress("UnstableApiUsage")
class NoImplImportsInAppModuleDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UImportStatement::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return NoImplImportHandler(context)
    }

    internal class NoImplImportHandler(private val context: JavaContext) : UElementHandler() {
        override fun visitImportStatement(node: UImportStatement) {
            val importPath = node.importReference?.asSourceString() ?: return

            // Check if importing from an impl package
            if (!isImplPackageImport(importPath)) {
                return
            }

            // Allow impl modules and internal modules to import from impl modules
            if (isInImplModule() || isInInternalModule()) {
                return
            }

            val apiSuggestion = suggestApiAlternative(importPath)
            context.report(
                NO_IMPL_IMPORTS_IN_APP_MODULE_ISSUE,
                node,
                context.getLocation(node),
                "Modules should not import from :*-impl modules.\n$apiSuggestion"
            )
        }

        private fun isInImplModule(): Boolean {
            // Check if the current file is in an impl module
            val uFile = context.uastFile
            if (uFile != null) {
                val packageName = uFile.packageName
                // If the package name contains .impl., we're in an impl module
                if (packageName.matches(Regex("com\\.duckduckgo\\.[^.]+\\.impl\\..*"))) {
                    return true
                }
            }

            // Also check file path
            val filePath = context.file.path.replace('\\', '/')
            return filePath.contains("-impl/src/") || filePath.contains("-impl\\src\\")
        }

        private fun isInInternalModule(): Boolean {
            // Check if the current file is in an internal module (internal flavor)
            val uFile = context.uastFile
            if (uFile != null) {
                val packageName = uFile.packageName
                // If the package name contains .internal., we're in an internal module
                if (packageName.matches(Regex("com\\.duckduckgo\\.[^.]+\\.internal\\..*"))) {
                    return true
                }
            }

            // Also check file path
            val filePath = context.file.path.replace('\\', '/')
            return filePath.contains("-internal/src/") || filePath.contains("-internal\\src\\")
        }

        private fun isImplPackageImport(importPath: String): Boolean {
            // Check if the import is from com.duckduckgo.*.impl.*
            return importPath.matches(Regex("com\\.duckduckgo\\.[^.]+\\.impl\\..*"))
        }

        private fun suggestApiAlternative(implImport: String): String {
            // Try to suggest the API package equivalent
            val apiImport = implImport.replace("\\.impl\\.", ".api.")
            return "Consider using the public API instead: $apiImport\nIf the API doesn't expose what you need, extend the API module first."
        }
    }

    companion object {
        val NO_IMPL_IMPORTS_IN_APP_MODULE_ISSUE = Issue.create(
            "NoImplImportsInAppModule",
            "Modules should not import from :*-impl modules",
            """
                Only :*-impl modules and :*-internal modules should import from :*-impl modules.
                All other modules should depend on public APIs exposed through :*-api modules.
                Implementation details in :*-impl modules should remain internal.

                If you need access to functionality not exposed by the API module:
                1. Consider whether the functionality should be public
                2. If yes, add it to the corresponding :*-api module
                3. If no, reconsider your architecture - you shouldn't need internal implementation details
            """.trimIndent(),
            Category.CORRECTNESS,
            10,
            Severity.ERROR,
            Implementation(
                NoImplImportsInAppModuleDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES)
            )
        )
    }
}
