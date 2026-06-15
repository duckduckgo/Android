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

package com.duckduckgo.browsermode.api

import javax.inject.Qualifier

/**
 * DI qualifier for bindings that belong to the [BrowserMode.REGULAR] browsing experience.
 *
 * Annotate a binding or injection site with this to select the regular-mode variant when
 * both a regular and a fire-mode binding exist for the same type.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class RegularMode

/**
 * DI qualifier for bindings that belong to the [BrowserMode.FIRE] browsing experience.
 *
 * Annotate a binding or injection site with this to select the fire-mode variant when
 * both a regular and a fire-mode binding exist for the same type.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class FireMode
