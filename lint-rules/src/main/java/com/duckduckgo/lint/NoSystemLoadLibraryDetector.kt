/*
 * Copyright (c) 2022 DuckDuckGo
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
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.tryResolveNamed
import java.util.*

@Suppress("UnstableApiUsage")
class NoSystemLoadLibraryDetector : Detector(), SourceCodeScanner {
    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = NoInternalImportHandler(context)

    internal class NoInternalImportHandler(private val context: JavaContext) : UElementHandler() {
        override fun visitCallExpression(node: UCallExpression) {
            if (node.methodName == "loadLibrary" && node.receiver?.tryResolveNamed()?.name == "System") {
                context.report(NO_SYSTEM_LOAD_LIBRARY, node, context.getNameLocation(node), "System.loadLibrary() should not be used.")
            }
        }
    }

    companion object {
        val NO_SYSTEM_LOAD_LIBRARY = Issue.create("NoSystemLoadLibrary",
            "Do not use System.loadLibrary().",
            """
                System.loadLibrary() should not be used.
                Use LibraryLoader.loadLibrary() instead.
            """.trimIndent(),
            Category.CORRECTNESS, 10, ERROR,
            Implementation(NoSystemLoadLibraryDetector::class.java, EnumSet.of(JAVA_FILE, TEST_SOURCES))
        )
    }
}
