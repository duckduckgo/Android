/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.service

import com.duckduckgo.mobile.android.vpn.BuildConfig

private val EXCLUDED_SYSTEM_APPS = listOf(
    "com.android.vending",
    "com.google.android.gsf.login",
    "com.google.android.googlequicksearchbox",
    "com.google.android.gms",
    "com.google.android.backuptransport"
)

private val EXCLUDED_PROBLEMATIC_APPS = listOf(
    "com.facebook.katana",
    "com.facebook.lite",
    "com.facebook.orca",
    "com.facebook.mlite",
    "com.instagram.android"
)

private val FIRST_PARTY_TRACKERS_ONLY_APPS = listOf(
    "com.google.android.youtube"
)

private val MAJOR_BROWSERS = listOf(
    "com.duckduckgo.mobile.android",
    "com.duckduckgo.mobile.android.debug",
    "com.android.chrome",
    "org.mozilla.firefox",
    "com.opera.browser",
    "com.microsoft.emmx",
    "com.brave.browser",
    "com.UCMobile.intl",
    "com.android.browser",
    "com.sec.android.app.sbrowser",
    "info.guardianproject.orfo",
    "org.torproject.torbrowser_alpha",
    "mobi.mgeek.TunnyBrowser",
    "com.linkbubble.playstore",
    "org.adblockplus.browser",
    "arun.com.chromer",
    "com.flynx",
    "com.ghostery.android.ghostery",
    "com.cliqz.browser",
    "om.opera.mini.native",
    "com.uc.browser.en",
    "com.chrome.beta",
    "org.mozilla.firefox_beta",
    "com.opera.browser.beta",
    "com.opera.mini.native.beta",
    "com.sec.android.app.sbrowser.beta",
    "org.mozilla.fennec_fdroid",
    "org.mozilla.rocket",
    "com.chrome.dev",
    "com.chrome.canary",
    "corg.mozilla.fennec_aurora",
    "oorg.mozilla.fennec",
    "com.google.android.apps.chrome",
    "org.chromium.chrome"
)

val EXCLUDED_APPS =
    listOf(BuildConfig.LIBRARY_PACKAGE_NAME)
        .plus(EXCLUDED_SYSTEM_APPS)
        .plus(EXCLUDED_PROBLEMATIC_APPS)
        .plus(FIRST_PARTY_TRACKERS_ONLY_APPS)
        .plus(MAJOR_BROWSERS)
