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

package com.duckduckgo.espresso

import androidx.test.espresso.IdlingResource
import androidx.test.espresso.IdlingResource.ResourceCallback

class WaitTimeIdlingResource(private val waitTimeInMillis: Long) : IdlingResource {
    private val startTime: Long = System.currentTimeMillis()
    private lateinit var resourceCallback: ResourceCallback

    override fun getName(): String = javaClass.name

    override fun isIdleNow(): Boolean {
        val elapsed = System.currentTimeMillis() - startTime
        val idle: Boolean = elapsed >= waitTimeInMillis
        if (idle) {
            resourceCallback.onTransitionToIdle()
        }
        return idle
    }

    override fun registerIdleTransitionCallback(callback: ResourceCallback) {
        this.resourceCallback = callback
    }
}
