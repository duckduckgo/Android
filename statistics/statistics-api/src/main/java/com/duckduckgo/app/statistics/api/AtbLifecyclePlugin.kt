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

package com.duckduckgo.app.statistics.api

interface AtbLifecyclePlugin {
    /**
     * Will be called right after we have refreshed the ATB retention on search
     */
    fun onSearchRetentionAtbRefreshed(oldAtb: String, newAtb: String) {
        // default is no-op
    }

    /**
     * Will be called right after we have refreshed the ATB retention on search
     */
    fun onAppRetentionAtbRefreshed(oldAtb: String, newAtb: String) {
        // default is no-op
    }

    /**
     * Will be called right after we have refreshed the ATB retention on duck.ai
     */
    fun onDuckAiRetentionAtbRefreshed(oldAtb: String, newAtb: String) {
        // default is no-op
    }

    /**
     * Will be called right after the ATB is first initialized and successfully sent via exti call
     */
    fun onAppAtbInitialized() {
        // default is no-op
    }
}
