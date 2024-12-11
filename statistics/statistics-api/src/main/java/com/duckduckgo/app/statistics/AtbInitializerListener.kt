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

package com.duckduckgo.app.statistics

interface AtbInitializerListener {

    /** This method will be called before initializing the ATB */
    suspend fun beforeAtbInit()

    /** @return the timeout in milliseconds after which [beforeAtbInit] will be stopped */
    fun beforeAtbInitTimeoutMillis(): Long

    companion object {
        const val PRIORITY_REINSTALL_LISTENER = 10
        const val PRIORITY_AURA_EXPERIMENT_MANAGER = 20
    }
}
