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
import java.util.EnumSet

/**
 * Freezes the toggle surface of the `browser-feature-toggles` module.
 *
 * [com.duckduckgo.browser.feature.toggles.AndroidBrowserConfigFeature] accumulated dozens of
 * unrelated toggles because it was the path of least resistance for anything browser-shaped. Every toggle
 * declared here forces `:app` (and any other consumer) to depend on the whole module, and leaves
 * the flag far away from the code it gates. New toggles belong to the feature that owns them.
 *
 * The grandfathered set is deliberately hardcoded rather than baselined: adding an entry has to
 * happen in the same diff as the toggle, where a reviewer will see it.
 */
@Suppress("UnstableApiUsage")
class NoNewBrowserFeatureToggleDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = Handler(context)

    internal class Handler(private val context: JavaContext) : UElementHandler() {

        override fun visitClass(node: UClass) {
            val qualifiedName = node.qualifiedName ?: return
            if (!qualifiedName.startsWith("$FROZEN_PACKAGE.")) return
            if (node.findAnnotation(CONTRIBUTES_REMOTE_FEATURE_FQN) == null) return

            // Every method on a @ContributesRemoteFeature interface is a toggle by construction,
            // so there is no need to resolve return types.
            for (method in node.methods) {
                if ("${node.name}#${method.name}" in GRANDFATHERED_TOGGLES) continue

                context.report(
                    NO_NEW_BROWSER_FEATURE_TOGGLE,
                    method,
                    context.getNameLocation(method),
                    NO_NEW_BROWSER_FEATURE_TOGGLE.getBriefDescription(TEXT),
                )
            }
        }
    }

    companion object {
        private const val CONTRIBUTES_REMOTE_FEATURE_FQN = "com.duckduckgo.anvil.annotations.ContributesRemoteFeature"

        /**
         * The module namespace is aligned 1:1 with the module, so the package prefix identifies the
         * module that lint gives no direct handle on.
         */
        private const val FROZEN_PACKAGE = "com.duckduckgo.browser.feature.toggles"

        /**
         * Toggles that already existed when this rule was introduced, keyed as `Interface#method`.
         * Do not add to this set — declare the toggle in the module that owns the feature instead.
         */
        private val GRANDFATHERED_TOGGLES = setOf(
            "AndroidBrowserConfigFeature#self",
            "AndroidBrowserConfigFeature#collectFullWebViewVersion",
            "AndroidBrowserConfigFeature#screenLock",
            "AndroidBrowserConfigFeature#cachedEntityLookup",
            "AndroidBrowserConfigFeature#stripWebViewImeInsets",
            "AndroidBrowserConfigFeature#errorPagePixel",
            "AndroidBrowserConfigFeature#errorCodePixel",
            "AndroidBrowserConfigFeature#featuresRequestHeader",
            "AndroidBrowserConfigFeature#webLocalStorage",
            "AndroidBrowserConfigFeature#indexedDB",
            "AndroidBrowserConfigFeature#enableMaliciousSiteProtection",
            "AndroidBrowserConfigFeature#fireproofedIndexedDB",
            "AndroidBrowserConfigFeature#httpError5xxPixel",
            "AndroidBrowserConfigFeature#glideSuspend",
            "AndroidBrowserConfigFeature#omnibarAnimation",
            "AndroidBrowserConfigFeature#showNTPAfterIdleReturn",
            "AndroidBrowserConfigFeature#ntpAsDefaultAfterIdleReturn",
            "AndroidBrowserConfigFeature#storeFaviconSuspend",
            "AndroidBrowserConfigFeature#atomicFaviconWrites",
            "AndroidBrowserConfigFeature#checkMaliciousAfterHttpsUpgrade",
            "AndroidBrowserConfigFeature#newThreatProtectionSettings",
            "AndroidBrowserConfigFeature#handleIntentScheme",
            "AndroidBrowserConfigFeature#hideDuckAiInSerpKillSwitch",
            "AndroidBrowserConfigFeature#validateIntentResolution",
            "AndroidBrowserConfigFeature#establishedAppStageGuard",
            "AndroidBrowserConfigFeature#vpnMenuItem",
            "AndroidBrowserConfigFeature#vpnMenuItemInternational",
            "AndroidBrowserConfigFeature#splitOmnibar",
            "AndroidBrowserConfigFeature#splitOmnibarWelcomePage",
            "AndroidBrowserConfigFeature#reportWebViewCapabilities",
            "AndroidBrowserConfigFeature#useUrlPredictor",
            "AndroidBrowserConfigFeature#newCustomTab",
            "AndroidBrowserConfigFeature#onboardingDuckAiCopyUpdatesFeb26",
            "AndroidBrowserConfigFeature#sendVerifiedInstallPixels",
            "AndroidBrowserConfigFeature#refreshDuckAiOnSubscriptionChanges",
            "AndroidBrowserConfigFeature#disableTrackerAnimationOnRestart",
            "AndroidBrowserConfigFeature#sendDataClearingWideEvent",
            "AndroidBrowserConfigFeature#reduceBrowserTabBundleSize",
            "AndroidBrowserConfigFeature#sendPageLoadWideEvent",
            "AndroidBrowserConfigFeature#storePageContext",
            "AndroidBrowserConfigFeature#tabStateRestorationFix",
            "AndroidBrowserConfigFeature#externalPdfHandler",
            "AndroidBrowserConfigFeature#redirectDuckAiLinksFromCustomTab",
            "AndroidBrowserConfigFeature#webViewSessionPersistence",
            "AndroidBrowserConfigFeature#recreateAwareLifecycle",
            "AndroidBrowserConfigFeature#useFireAppShortcutTrampoline",
            "AndroidBrowserConfigFeature#customTabEndlessLoopFix",
            "AndroidBrowserConfigFeature#preserveCertificateOnSameOrigin",
        )

        val NO_NEW_BROWSER_FEATURE_TOGGLE = Issue.create(
            id = "NoNewBrowserFeatureToggle",
            briefDescription = "Do not add new feature toggles to the browser-feature-toggles module",
            explanation = """
                The `browser-feature-toggles` module is frozen. `AndroidBrowserConfigFeature` is a \
                catch-all that grew to dozens of unrelated toggles, and it is not accepting more.

                A toggle declared here forces every consumer to depend on the whole module and \
                puts the flag far away from the code it gates.

                Declare the toggle in the module that owns the feature instead:

                * If only one `-impl` module reads the toggle, put the `@ContributesRemoteFeature` \
                interface in that `-impl` module.
                * If several modules read it, put the interface in its own small standalone module. \
                It cannot live in an `-api` module (Anvil is banned there) and `:app` cannot import \
                from an `-impl` module.

                Do not add an entry to the grandfathered list to silence this check.
            """,
            category = Category.CORRECTNESS,
            priority = 10,
            severity = ERROR,
            implementation = Implementation(
                NoNewBrowserFeatureToggleDetector::class.java,
                EnumSet.of(JAVA_FILE, TEST_SOURCES),
            ),
        )
    }
}
