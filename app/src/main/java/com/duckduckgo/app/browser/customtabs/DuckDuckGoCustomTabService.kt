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
        Timber.d("warmup called with flags=$flags")
        return true
    }

    override fun newSession(sessionToken: CustomTabsSessionToken): Boolean {
        val uid = Binder.getCallingUid()
        val packageName = packageManager.getPackagesForUid(uid)?.singleOrNull()
        Timber.d("newSession called with sessionToken=$sessionToken; uid=$uid and packageName=$packageName")
        return true
    }

    override fun mayLaunchUrl(
        sessionToken: CustomTabsSessionToken,
        url: Uri?,
        extras: Bundle?,
        otherLikelyBundles: MutableList<Bundle>?,
    ): Boolean {
        Timber.d(
            "mayLaunchUrl called with sessionToken=$sessionToken, url=$url, extras=$extras, otherLikelyBundles=$otherLikelyBundles",
        )
        return true
    }

    override fun extraCommand(commandName: String, args: Bundle?): Bundle? {
        Timber.d("extraCommand called with commandName=$commandName and args=$args")
        return null
    }

    override fun updateVisuals(sessionToken: CustomTabsSessionToken, bundle: Bundle?): Boolean {
        Timber.d("updateVisuals called with sessionToken=$sessionToken and bundle=$bundle")
        return false
    }

    override fun requestPostMessageChannel(
        sessionToken: CustomTabsSessionToken,
        postMessageOrigin: Uri,
    ): Boolean {
        Timber.d("requestPostMessageChannel called with sessionToken=$sessionToken and postMessageOrigin=$postMessageOrigin")
        return false
    }

    override fun postMessage(
        sessionToken: CustomTabsSessionToken,
        message: String,
        extras: Bundle?,
    ): Int {
        Timber.d("postMessage called with sessionToken=$sessionToken, message=$message and extras=$extras")
        return RESULT_FAILURE_DISALLOWED
    }

    override fun validateRelationship(
        sessionToken: CustomTabsSessionToken,
        relation: Int,
        origin: Uri,
        extras: Bundle?,
    ): Boolean {
        Timber.d("validateRelationship called with sessionToken=$sessionToken, relation=$relation, origin=$origin, extras=$extras")
        return true
    }

    override fun receiveFile(
        sessionToken: CustomTabsSessionToken,
        uri: Uri,
        purpose: Int,
        extras: Bundle?,
    ): Boolean {
        Timber.d("receiveFile called with sessionToken=$sessionToken, uri=$uri, purpose=$purpose and extras=$extras")
        return false
    }
}
