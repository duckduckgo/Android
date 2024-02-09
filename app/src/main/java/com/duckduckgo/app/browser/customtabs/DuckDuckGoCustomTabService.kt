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

package com.duckduckgo.app.browser.customtabs

import android.net.Uri
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsService
import androidx.browser.customtabs.CustomTabsSessionToken

class DuckDuckGoCustomTabService: CustomTabsService() {
    override fun warmup(flags: Long): Boolean {
        println("TAG_ANA warmup called with $flags")
        return false
    }

    override fun newSession(sessionToken: CustomTabsSessionToken): Boolean {
        println("TAG_ANA newSession called with $sessionToken")
        return false
    }

    override fun mayLaunchUrl(
        sessionToken: CustomTabsSessionToken,
        url: Uri?,
        extras: Bundle?,
        otherLikelyBundles: MutableList<Bundle>?
    ): Boolean {
        println("TAG_ANA mayLaunchUrl called with $sessionToken and $url and $extras and $otherLikelyBundles")
        return true
    }

    override fun extraCommand(commandName: String, args: Bundle?): Bundle? {
        println("TAG_ANA extraCommand called with $commandName and $args")
        return null
    }

    override fun updateVisuals(sessionToken: CustomTabsSessionToken, bundle: Bundle?): Boolean {
        println("TAG_ANA updateVisuals called with $sessionToken and $bundle")
        return false
    }

    override fun requestPostMessageChannel(sessionToken: CustomTabsSessionToken, postMessageOrigin: Uri): Boolean {
        println("TAG_ANA requestPostMessageChannel called with $sessionToken and $postMessageOrigin")
        return false
    }

    override fun postMessage(sessionToken: CustomTabsSessionToken, message: String, extras: Bundle?): Int {
        println("TAG_ANA postMessage called with $sessionToken and $message and $extras")
        return CustomTabsService.RESULT_FAILURE_DISALLOWED
    }

    override fun validateRelationship(sessionToken: CustomTabsSessionToken, relation: Int, origin: Uri, extras: Bundle?): Boolean {
        println("TAG_ANA validateRelationship called with $sessionToken and $relation and $origin and $extras")
        return false
    }

    override fun receiveFile(sessionToken: CustomTabsSessionToken, uri: Uri, purpose: Int, extras: Bundle?): Boolean {
        println("TAG_ANA receiveFile called with $sessionToken and $uri and $purpose and $extras")
        return false
    }
}