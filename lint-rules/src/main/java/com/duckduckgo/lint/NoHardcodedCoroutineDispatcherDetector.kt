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

@file:Suppress("UnstableApiUsage")

package com.duckduckgo.lint

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope.JAVA_FILE
import com.android.tools.lint.detector.api.Scope.TEST_SOURCES
import com.android.tools.lint.detector.api.Severity.ERROR
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.getContainingUClass
import java.util.*

class NoHardcodedCoroutineDispatcherDetector : Detector(), SourceCodeScanner {

    override fun getApplicableReferenceNames(): List<String> = listOf("Dispatchers")

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement
    ) {
        val classUnderInspection = reference.getContainingUClass()?.qualifiedName
        if (EXCLUDED_SOURCE_FILES.contains(classUnderInspection)) return

        val referencedPackage = context.evaluator.getPackage(referenced)?.qualifiedName
        val referencedClassName = reference.resolvedName

        when ("${referencedPackage}.${referencedClassName}") {
            "kotlinx.coroutines.Dispatchers" -> {
                context.report(NO_HARCODED_COROUTINE_DISPATCHER, reference, context.getLocation(reference), ERROR_DESCRIPTION)
            }
        }
    }

    companion object {
        const val ERROR_ID = "NoHardcodedCoroutineDispatcher"
        const val ERROR_DESCRIPTION = "Hardcoded coroutine dispatcher"

        private val EXCLUDED_SOURCE_FILES = listOf(
            "com.duckduckgo.common.utils.DispatcherProvider",
            "com.duckduckgo.common.test.CoroutineTestRule"
        )

        val NO_HARCODED_COROUTINE_DISPATCHER = Issue.create(
            ERROR_ID,
            ERROR_DESCRIPTION,
            "Inject com.duckduckgo.common.utils.DispatcherProvider instead.",
            Category.CORRECTNESS, 10, ERROR,
            Implementation(NoHardcodedCoroutineDispatcherDetector::class.java, EnumSet.of(JAVA_FILE, TEST_SOURCES))
        )
    }
}
