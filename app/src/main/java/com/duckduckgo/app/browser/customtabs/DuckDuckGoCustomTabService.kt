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
import android.os.Binder
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsService
import androidx.browser.customtabs.CustomTabsSessionToken
import timber.log.Timber

class DuckDuckGoCustomTabService : CustomTabsService() {
    override fun warmup(flags: Long): Boolean {
        Timber.d("TAG_CUSTOM_TAB_IMPL warmup called in DuckDuckGoCustomTabService with flags: $flags")
        return true
    }

    override fun newSession(sessionToken: CustomTabsSessionToken): Boolean {
        Timber.d("TAG_CUSTOM_TAB_IMPL newSession called in DuckDuckGoCustomTabService with sessionToken: $sessionToken")
        val uid = Binder.getCallingUid()
        val packageName = packageManager.getPackagesForUid(uid)?.singleOrNull()
        Timber.d("TAG_CUSTOM_TAB_IMPL newSession called in DuckDuckGoCustomTabService with uid: $uid -- packageName: $packageName")
        return true
    }

    override fun mayLaunchUrl(
        sessionToken: CustomTabsSessionToken,
        url: Uri?,
        extras: Bundle?,
        otherLikelyBundles: MutableList<Bundle>?,
    ): Boolean {
        Timber.d(
            "TAG_CUSTOM_TAB_IMPL mayLaunchUrl called in DuckDuckGoCustomTabService with sessionToken: $sessionToken -- url: $url -- " +
                "extras: $extras -- otherLikelyBundles: $otherLikelyBundles",
        )
        return true
    }

    override fun extraCommand(commandName: String, args: Bundle?): Bundle? {
        Timber.d("TAG_CUSTOM_TAB_IMPL extraCommand called in DuckDuckGoCustomTabService with commandName: $commandName -- args: $args")
        return null
    }

    override fun updateVisuals(sessionToken: CustomTabsSessionToken, bundle: Bundle?): Boolean {
        Timber.d("TAG_CUSTOM_TAB_IMPL updateVisuals called with $sessionToken and $bundle")
        return false
    }

    override fun requestPostMessageChannel(
        sessionToken: CustomTabsSessionToken,
        postMessageOrigin: Uri,
    ): Boolean {
        Timber.d(
            "TAG_CUSTOM_TAB_IMPL requestPostMessageChannel called in DuckDuckGoCustomTabService with sessionToken: $sessionToken -- " +
                "postMessageOrigin: $postMessageOrigin",
        )
        return false
    }

    override fun postMessage(
        sessionToken: CustomTabsSessionToken,
        message: String,
        extras: Bundle?,
    ): Int {
        Timber.d(
            "TAG_CUSTOM_TAB_IMPL postMessage called in DuckDuckGoCustomTabService with sessionToken: $sessionToken -- " +
                "message: $message -- extras: $extras",
        )
        return RESULT_FAILURE_DISALLOWED
    }

    override fun validateRelationship(
        sessionToken: CustomTabsSessionToken,
        relation: Int,
        origin: Uri,
        extras: Bundle?,
    ): Boolean {
        Timber.d(
            "TAG_CUSTOM_TAB_IMPL validateRelationship called in DuckDuckGoCustomTabService with sessionToken: $sessionToken -- " +
                "relation: $relation -- origin: $origin -- extras: $extras",
        )
        return true
    }

    override fun receiveFile(
        sessionToken: CustomTabsSessionToken,
        uri: Uri,
        purpose: Int,
        extras: Bundle?,
    ): Boolean {
        Timber.d(
            "TAG_CUSTOM_TAB_IMPL receiveFile called in DuckDuckGoCustomTabService with sessionToken: $sessionToken -- " +
                "uri: $uri -- purpose: $purpose -- extras: $extras",
        )
        return false
    }
}
