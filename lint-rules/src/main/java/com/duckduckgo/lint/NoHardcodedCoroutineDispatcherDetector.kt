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
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.getQualifiedChain
import org.jetbrains.uast.sourcePsiElement
import org.jetbrains.uast.tryResolveNamed
import java.util.*

@Suppress("UnstableApiUsage")
class NoHardcodedCoroutineDispatcherDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UQualifiedReferenceExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = CustomElementHandler(context)

    internal class CustomElementHandler(private val context: JavaContext) : UElementHandler() {

        override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
            val parts = node.getQualifiedChain()
            if (parts[0].tryResolveNamed()?.name == "Dispatchers") {
                val dispatcherType = parts[1].tryResolveNamed()?.name
                when (dispatcherType) {
                    "getIO",
                    "getMain",
                    "getUnconfined",
                    "getDefault" -> {
                        context.report(NO_HARCODED_COROUTINE_DISPATCHER, node.sourcePsiElement, context.getLocation(node), ERROR_DESCRIPTION)
                    }
                }
            }
        }
    }

    companion object {
        const val ERROR_ID = "NoHardcodedCoroutineDispatcher"
        const val ERROR_DESCRIPTION = "Hardcoded coroutine dispatcher"

        val NO_HARCODED_COROUTINE_DISPATCHER = Issue.create(
            ERROR_ID,
            ERROR_DESCRIPTION,
            "Inject com.duckduckgo.app.global.DispatcherProvider instead.",
            Category.CORRECTNESS, 10, ERROR,
            Implementation(NoHardcodedCoroutineDispatcherDetector::class.java, EnumSet.of(JAVA_FILE, TEST_SOURCES))
        )
    }
}
