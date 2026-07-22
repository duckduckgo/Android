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
import com.android.tools.lint.detector.api.Scope.JAVA_FILE
import com.android.tools.lint.detector.api.Scope.TEST_SOURCES
import com.android.tools.lint.detector.api.Severity.ERROR
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TextFormat.TEXT
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import java.util.EnumSet

/**
 * Detects underscores in [com.duckduckgo.anvil.annotations.ContributesRemoteFeature] interface
 * method names and in the `featureName` annotation parameter.
 *
 * SharedPreferences keys are stored as `"featureName_methodName"`. If either part contains an
 * underscore the key becomes ambiguous — the same string can be produced by different
 * (featureName, methodName) combinations, silently causing state to be read from the wrong key.
 */
@Suppress("UnstableApiUsage")
class RemoteFeatureNameDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = Handler(context)

    internal class Handler(private val context: JavaContext) : UElementHandler() {

        override fun visitClass(node: UClass) {
            val annotation = node.findAnnotation(CONTRIBUTES_REMOTE_FEATURE_FQN) ?: return

            // Check the featureName string value
            val featureNameExpr = annotation.findAttributeValue("featureName")
            val featureName = featureNameExpr?.evaluate() as? String
            if (featureName != null && featureName.contains('_')) {
                context.report(
                    UNDERSCORE_IN_FEATURE_NAME,
                    featureNameExpr as UElement,
                    context.getLocation(featureNameExpr as UElement),
                    UNDERSCORE_IN_FEATURE_NAME.getBriefDescription(TEXT),
                )
            }

            // Check every method name declared in the interface
            for (method in node.methods) {
                if (method.name.contains('_')) {
                    context.report(
                        UNDERSCORE_IN_FEATURE_NAME,
                        method,
                        context.getNameLocation(method),
                        UNDERSCORE_IN_FEATURE_NAME.getBriefDescription(TEXT),
                    )
                }
            }
        }
    }

    companion object {
        private const val CONTRIBUTES_REMOTE_FEATURE_FQN = "com.duckduckgo.anvil.annotations.ContributesRemoteFeature"

        val UNDERSCORE_IN_FEATURE_NAME = Issue.create(
            id = "RemoteFeatureNameWithUnderscore",
            briefDescription = "RemoteFeature names must not contain underscores",
            explanation = """
                The `featureName` parameter and method names in a `@ContributesRemoteFeature` interface \
                must not contain underscores.

                SharedPreferences keys are stored as `featureName_methodName`. If either part contains \
                an underscore the resulting key is ambiguous — the same string can be produced by \
                different (featureName, methodName) combinations, silently causing toggle state to be \
                read from or written to the wrong key.

                Use camelCase for both the feature name and method names.
            """,
            category = Category.CORRECTNESS,
            priority = 10,
            severity = ERROR,
            implementation = Implementation(
                RemoteFeatureNameDetector::class.java,
                EnumSet.of(JAVA_FILE, TEST_SOURCES),
            ),
        )
    }
}
