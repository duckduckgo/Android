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
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import java.util.EnumSet

@Suppress("UnstableApiUsage")
class NoPostValueOnSingleLiveEventDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> {
        return listOf("postValue")
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val receiverType = node.receiverType ?: return
        val rawType = if (receiverType is PsiClassType) receiverType.rawType() else receiverType
        val receiverClass = context.evaluator.findClass(rawType.canonicalText) ?: return

        if (isSingleLiveEventOrSubclass(context, receiverClass)) {
            context.report(
                NO_POST_VALUE_ON_SINGLE_LIVE_EVENT,
                node,
                context.getLocation(node),
                "Do not use `postValue()` on `SingleLiveEvent`. " +
                    "Use `setValue()` (`.value = ...`) on the main thread instead. " +
                    "`postValue()` coalesces pending values, which silently drops commands.",
            )
        }
    }

    private fun isSingleLiveEventOrSubclass(
        context: JavaContext,
        cls: com.intellij.psi.PsiClass,
    ): Boolean {
        if (cls.qualifiedName == SINGLE_LIVE_EVENT_CLASS) return true
        return context.evaluator.extendsClass(cls, SINGLE_LIVE_EVENT_CLASS, false)
    }

    companion object {
        private const val SINGLE_LIVE_EVENT_CLASS = "com.duckduckgo.common.utils.SingleLiveEvent"

        val NO_POST_VALUE_ON_SINGLE_LIVE_EVENT = Issue.create(
            id = "NoPostValueOnSingleLiveEvent",
            briefDescription = "Do not use postValue() on SingleLiveEvent",
            explanation = """
                `postValue()` on `SingleLiveEvent` can silently drop commands. \
                When multiple `postValue()` calls happen before the main thread processes them, \
                only the last value is delivered — earlier commands are lost without any error or log.

                This is especially dangerous for one-shot commands (navigation, dialogs, snackbars) \
                where every emission matters.

                Use `setValue()` (`.value = ...`) on the main thread instead. If you are on a \
                background thread, wrap in `withContext(dispatchers.main()) { command.value = ... }`.
            """,
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.ERROR,
            implementation = Implementation(
                NoPostValueOnSingleLiveEventDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
            ),
        )
    }
}