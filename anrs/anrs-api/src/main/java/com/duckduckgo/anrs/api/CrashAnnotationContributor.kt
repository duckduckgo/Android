/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.anrs.api

/**
 * Advertises a fixed set of annotation keys that a feature module wants to write
 * into crash reports at runtime via [CrashAnnotations].
 *
 * ## Why this exists
 *
 * The Crashpad native layer uses a fixed-size allowlist. Keys that were NOT registered
 * before Crashpad initialises are silently dropped from the minidump — they will never
 * appear in the crash report, even if you call [CrashAnnotations.set] at crash time.
 *
 * Every feature that calls [CrashAnnotations.set] MUST contribute a corresponding
 * [CrashAnnotationContributor] so its keys are pre-registered at startup.
 * [CrashBreadcrumbs] does NOT require a contributor — breadcrumb slots are
 * self-registered by the infrastructure.
 *
 * ## How to contribute
 *
 * 1. Define an internal object in your feature module that holds your key string constants,
 *    e.g. `internal object MyFeatureAnnotationKeys { const val MY_KEY = "my_feature_key" }`.
 * 2. Implement [CrashAnnotationContributor] in the same module and annotate it with
 *    `@ContributesMultibinding(AppScope::class)`.
 * 3. Return your key constants from [keys].
 *
 * ```kotlin
 * @ContributesMultibinding(AppScope::class)
 * class MyFeatureCrashAnnotationContributor @Inject constructor() : CrashAnnotationContributor {
 *     override val keys = setOf(
 *         MyFeatureAnnotationKeys.MY_KEY,
 *         MyFeatureAnnotationKeys.MY_OTHER_KEY,
 *     )
 * }
 * ```
 *
 * ## Key naming conventions
 *
 * Keys must be `snake_case` and prefixed with the feature name to avoid collisions:
 *   - `browser_url`
 *   - `browser_nav_state`
 *   - `vpn_tunnel_state`
 *
 * Key constants must live in your feature module alongside the contributor and call
 * sites — do NOT define them in `anrs-api`.
 *
 * ## Annotation budget
 *
 * The Crashpad dictionary has a hard limit of 64 entries across all keys from all
 * modules. Approximately 7 are consumed by static annotations (platform, version,
 * osVersion, format, customTab, webViewPackage, webViewVersion) and 11 by breadcrumbs
 * (crumb_0..crumb_9, crumb_idx). This leaves roughly 46 slots for all feature modules
 * combined. A debug/internal-build assertion fires if the total exceeds 60.
 */
interface CrashAnnotationContributor {
    /** The set of annotation keys this contributor will write at runtime. */
    val keys: Set<String>
}
