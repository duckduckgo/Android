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
import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.Severity.ERROR
import com.intellij.psi.impl.compiled.ClsClassImpl
import org.jetbrains.uast.UCallExpression
import java.util.*

@Suppress("UnstableApiUsage")
class NoRetrofitCreateMethodCallDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = NoInternalImportHandler(context)

    internal class NoInternalImportHandler(private val context: JavaContext) : UElementHandler() {

        override fun visitCallExpression(node: UCallExpression) {
            if ((node.resolve()?.parent as? ClsClassImpl)?.stub?.qualifiedName == "retrofit2.Retrofit" && node.methodName == "create") {
                context.report(
                    issue = NO_RETROFIT_CREATE_CALL,
                    location = context.getNameLocation(node),
                    message = NO_RETROFIT_CREATE_CALL.getExplanation(TextFormat.RAW)
                )
            }
        }
    }

    companion object {
        val NO_RETROFIT_CREATE_CALL = Issue.create(
            id = "NoRetrofitCreateMethodCallDetector",
            briefDescription = "Do not use retrofit.create().",
            explanation = """
                retrofit.create() should not be used directly.
                Use @ContributesServiceApi annotation instead.
            """.trimIndent(),
            moreInfo = "https://app.asana.com/0/0/1203425544997317/f",
            category = Category.CUSTOM_LINT_CHECKS,
            priority = 10,
            severity = ERROR,
            implementation = Implementation(NoRetrofitCreateMethodCallDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}
