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

package com.duckduckgo.autofill.api

/**
 * Public API that could be used if the user is a verified internal tester.
 * This class could potentially be moved to a different module once there's a need for it in the future.
 */
interface InternalTestUserChecker {
    /**
     * This checks if the user has went through the process of becoming a verified test user
     */
    val isInternalTestUser: Boolean

    /**
     * This method should be called if an error is received when loading a [url].
     * This will only be processed if the [url] passed is a valid internal tester success verification url
     * else it will just be ignored.
     */
    fun verifyVerificationErrorReceived(url: String)

    /**
     * This method should be called if the [url] is completely loaded.
     * This will only be processed if the [url] passed is a valid internal tester success verification url
     * else it will just be ignored.
     */
    fun verifyVerificationCompleted(url: String)
}
