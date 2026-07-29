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

package com.duckduckgo.common.ui.store

/**
 * Exposes the appBrandDesignUpdate feature flag to the theming layer.
 * :design-system cannot read feature flags directly; :app binds the implementation.
 * Interim scaffolding for the brand design update, deleted with the flag by the
 * follow-up theme project.
 */
interface AppBrandDesignThemeProvider {
    fun isAppBrandDesignUpdateEnabled(): Boolean
}
