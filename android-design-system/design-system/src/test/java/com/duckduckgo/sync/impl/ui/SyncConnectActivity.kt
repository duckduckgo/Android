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

package com.duckduckgo.sync.impl.ui

import androidx.appcompat.app.AppCompatActivity

/**
 * Test double, not the production class. Its fully qualified name deliberately matches the entry in
 * Theming.Constants.FIXED_THEME_ACTIVITIES so that localClassName resolves to a fixed-theme
 * activity. Safe because design-system does not depend on sync-impl.
 */
class SyncConnectActivity : AppCompatActivity()
