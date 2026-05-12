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

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope.JAVA_FILE
import com.android.tools.lint.detector.api.Scope.TEST_SOURCES
import com.android.tools.lint.detector.api.Severity.WARNING
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.getContainingUClass
import java.util.EnumSet

class NoLifecycleScopeInFragmentDetector : Detector(), SourceCodeScanner {

    override fun getApplicableReferenceNames(): List<String> = listOf("lifecycleScope")

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement,
    ) {
        val containingClass = reference.getContainingUClass() ?: return

        if (!context.evaluator.extendsClass(containingClass, FRAGMENT_CLASS, false)) return

        val parent = reference.uastParent
        if (parent is UQualifiedReferenceExpression) {
            val receiverText = parent.receiver.asSourceString()
            if (receiverText == "viewLifecycleOwner") return
        }

        context.report(
            NO_LIFECYCLE_SCOPE_IN_FRAGMENT,
            reference,
            context.getLocation(reference),
            ERROR_DESCRIPTION,
        )
    }

    companion object {
        private const val FRAGMENT_CLASS = "androidx.fragment.app.Fragment"

        const val ERROR_ID = "NoLifecycleScopeInFragment"
        const val ERROR_DESCRIPTION =
            "Use viewLifecycleOwner.lifecycleScope instead of lifecycleScope in Fragments. " +
                "Fragment views can be destroyed while the fragment is still alive (backstack, ViewPager), " +
                "which can cause crashes when coroutines try to update destroyed views."

        val NO_LIFECYCLE_SCOPE_IN_FRAGMENT: Issue = Issue.create(
            ERROR_ID,
            "lifecycleScope should not be used directly in Fragments",
            """
                Using lifecycleScope in a Fragment ties the coroutine to the Fragment's lifecycle
                rather than the view's lifecycle. Since Fragment views can be destroyed and recreated
                independently of the Fragment itself (e.g. when added to the backstack or used in a
                ViewPager), coroutines launched with lifecycleScope may outlive the view and crash
                when updating destroyed UI elements.

                Use viewLifecycleOwner.lifecycleScope instead, which is tied to the view lifecycle
                and will automatically cancel coroutines when the view is destroyed.
            """.trimIndent(),
            Category.CORRECTNESS,
            10,
            WARNING,
            Implementation(NoLifecycleScopeInFragmentDetector::class.java, EnumSet.of(JAVA_FILE, TEST_SOURCES)),
        )
    }
}
