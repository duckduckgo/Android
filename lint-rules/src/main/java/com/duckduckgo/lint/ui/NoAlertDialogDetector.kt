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

package com.duckduckgo.lint.ui

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TextFormat
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

@Suppress("UnstableApiUsage")
class NoAlertDialogDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames() = listOf("setView", "create")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val evaluator = context.evaluator
        if (!evaluator.methodMatches(method, MATERIAL_ALERT_DIALOG_BUILDER, true)) {
            return
        }
        context.report(
            issue = NO_DESIGN_SYSTEM_DIALOG,
            location = context.getNameLocation(method),
            message = NO_DESIGN_SYSTEM_DIALOG.getExplanation(TextFormat.RAW)
        )
    }

    companion object {

        private const val MATERIAL_ALERT_DIALOG_BUILDER = "MaterialAlertDialogBuilder"
        private const val APP_COMPAT_ALERT_DIALOG_BUILDER = "androidx.appcompat.app.AlertDialog.Builder"
        private const val ALERT_DIALOG_BUILDER = "android.app.AlertDialog.Builder"

        val NO_DESIGN_SYSTEM_DIALOG = Issue
            .create(
                id = "NoDesignSystemDialog",
                briefDescription = "Prohibits usages of AlertDialog and MaterialAlertDialog.",
                explanation = "Android Dialog used instead of Design System Component. Always favor the use of the Design System Dialogs",
                moreInfo = "https://app.asana.com/0/1202857801505092/1202943892847393",
                category = Category.CUSTOM_LINT_CHECKS,
                priority = 10,
                severity = Severity.ERROR,
                androidSpecific = true,
                implementation = Implementation(
                    NoAlertDialogDetector::class.java,
                    Scope.JAVA_FILE_SCOPE
                )
            )
    }
}
