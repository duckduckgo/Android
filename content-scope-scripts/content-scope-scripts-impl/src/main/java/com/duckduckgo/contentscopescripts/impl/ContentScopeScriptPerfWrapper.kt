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

package com.duckduckgo.contentscopescripts.impl

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import javax.inject.Inject

/**
 * Adds User Timing instrumentation around the assembled content-scope bundle.
 *
 * Android WebView has no isolated world, so `window.__ddgPerf` is visible to the page — the
 * internal-build gate is a correctness requirement here, not a size optimisation.
 *
 * The `sourceURL` trailer is what makes V8's compile and evaluate cost attributable: compilation
 * happens before the bundle's first statement, so it cannot be self-measured, and an injected
 * bundle is otherwise anonymous in the DevTools Performance panel.
 */
class ContentScopeScriptPerfWrapper @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
) {
    fun wrap(script: String): String {
        if (!appBuildConfig.isInternalBuild()) return script
        val bytes = script.toByteArray(Charsets.UTF_8).size
        return PROLOGUE + script + epilogue(bytes)
    }

    // The bundle is deliberately NOT wrapped in a try: that would swallow its errors and change its
    // semantics. If it throws, the appended code simply never runs and no measure is recorded.
    private fun epilogue(bytes: Int) =
        """
        |
        |;(function () {
        |  try {
        |    performance.mark('ddg-cs-end');
        |    var m = performance.measure('ddg-contentscope', 'ddg-cs-start', 'ddg-cs-end') ||
        |      performance.getEntriesByName('ddg-contentscope').pop();
        |    window.__ddgPerf = {
        |      bytes: $bytes,
        |      execMs: m.duration,
        |      sinceNavStartMs: m.startTime,
        |      get fcpMs() {
        |        try {
        |          var e = performance.getEntriesByName('first-contentful-paint');
        |          return e.length ? e[0].startTime : null;
        |        } catch (e) { return null; }
        |      }
        |    };
        |  } catch (e) {}
        |})();
        |//# sourceURL=ddg-contentscope.js
        """.trimMargin()

    companion object {
        // Guarded because this is PREPENDED: an unguarded throw here (a page that clobbers
        // window.performance, as anti-bot scripts do) would abort evaluation before the bundle's
        // first statement, silently disabling every content-scope protection for that page load.
        private const val PROLOGUE = "try { performance.mark('ddg-cs-start') } catch (e) {}\n"
    }
}
