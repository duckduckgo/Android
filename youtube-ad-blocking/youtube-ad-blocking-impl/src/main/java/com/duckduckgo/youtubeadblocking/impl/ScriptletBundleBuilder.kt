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

package com.duckduckgo.youtubeadblocking.impl

/**
 * Builds the scriptlet bundle string with BEFORE/AFTER timing markers around each scriptlet.
 *
 * Output in logcat (via console.log):
 * ```
 * [TAG] BEFORE MAIN scriptlet (112981 bytes) at 0.42ms | ytInitialData: false | ytcfg: false | ytPlayerResponse: false
 * ... main scriptlet runs ...
 * [TAG] AFTER MAIN scriptlet at 12.3ms | ytInitialData: false | ytcfg: false | ytPlayerResponse: false
 * [TAG] BEFORE ISOLATED scriptlet (33992 bytes) at 12.5ms | ytInitialData: false | ytcfg: false | ytPlayerResponse: false
 * ... isolated scriptlet runs ...
 * [TAG] AFTER ISOLATED scriptlet at 15.1ms | ytInitialData: false | ytcfg: true | ytPlayerResponse: false
 * ```
 *
 * If BEFORE shows `false` but AFTER shows `true`, YouTube's init ran *during* that scriptlet.
 */
object ScriptletBundleBuilder {

    fun buildScriptlets(
        tag: String,
        includeMain: Boolean,
        mainJs: String?,
        includeIsolated: Boolean,
        isolatedJs: String?,
    ): String {
        return buildString {
            if (includeMain && mainJs != null) {
                append(timingCheck(tag, "BEFORE MAIN scriptlet (${mainJs.length} bytes)"))
                append(mainJs)
                append("\n")
                append(timingCheck(tag, "AFTER MAIN scriptlet"))
            }
            if (includeIsolated && isolatedJs != null) {
                if (isNotEmpty()) append("\n")
                append(timingCheck(tag, "BEFORE ISOLATED scriptlet (${isolatedJs.length} bytes)"))
                append(isolatedJs)
                append("\n")
                append(timingCheck(tag, "AFTER ISOLATED scriptlet"))
            }
            if (!includeMain && !includeIsolated) {
                append("console.log('[$tag] No scriptlets enabled (injectMain=false, injectIsolated=false)');\n")
            }
        }
    }

    private fun timingCheck(tag: String, label: String): String {
        return """console.log('[$tag] $label at ' + performance.now().toFixed(2) + 'ms | ytInitialData: ' + (typeof window.ytInitialData !== 'undefined') + ' | ytcfg: ' + (typeof window.ytcfg !== 'undefined') + ' | ytPlayerResponse: ' + (typeof window.ytInitialPlayerResponse !== 'undefined'));
"""
    }
}
