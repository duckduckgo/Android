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
import org.jetbrains.uast.UClass
import java.util.*

@Suppress("UnstableApiUsage")
class NoLifecycleObserverDetector : Detector(), SourceCodeScanner {
    override fun getApplicableUastTypes() = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = InternalDetectorHandler(context)

    internal class InternalDetectorHandler(private val context: JavaContext) : UElementHandler() {
        override fun visitClass(node: UClass) {
            if (node.implementsListTypes.any { bannedObserverClassNames.contains(it.className) }) {
                context.report(NO_LIFECYCLE_OBSERVER_ISSUE, node, context.getNameLocation(node), "LifecycleObserver should not be directly extended")
            }
        }
    }

    companion object {
        private val bannedObserverClassNames = listOf(
            "LifecycleEventObserver",
            "LifecycleObserver",
            "DefaultLifecycleObserver",
            "FullLifecycleObserver",
        )
        val NO_LIFECYCLE_OBSERVER_ISSUE = Issue.create("NoLifecycleObserver",
            "The LifecycleObserver classes should not be extended.",
            """
                Use MainProcessLifecycleObserver instead if you want to observe the lifecycle of the main process application.
                Use VpnProcessLifecycleObserver instead if you want to observe the onCreate lifecycle of the vpn process application.
            """.trimIndent(),
            Category.CORRECTNESS, 10, ERROR,
            Implementation(NoLifecycleObserverDetector::class.java, EnumSet.of(JAVA_FILE, TEST_SOURCES))
        )
    }
}
