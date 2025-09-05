/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.global.api

import com.duckduckgo.app.browser.WebViewPixelName
import com.duckduckgo.app.browser.httperrors.HttpErrorPixelName
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.AppPixelName.SITE_NOT_WORKING_SHOWN
import com.duckduckgo.app.pixels.AppPixelName.SITE_NOT_WORKING_WEBSITE_BROKEN
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.AppUrl
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.common.utils.plugins.pixel.PixelInterceptorPlugin
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin.PixelParameter
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin.PixelParameter.APP_VERSION
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin.PixelParameter.ATB
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.site.permissions.impl.SitePermissionsPixelName
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Response

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PixelInterceptorPlugin::class,
)
class PixelParamRemovalInterceptor @Inject constructor(
    private val pixelsPlugin: PluginPoint<PixelParamRemovalPlugin>,
) : Interceptor, PixelInterceptorPlugin {

    val pixels by lazy {
        pixelsPlugin.getPlugins().flatMap { it.names() }.toSet()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
        val pixel = chain.request().url.pathSegments.last()
        val url = chain.request().url.newBuilder().apply {
            val atbs = pixels.filter { it.second.contains(ATB) }.map { it.first }
            val versions = pixels.filter { it.second.contains(APP_VERSION) }.map { it.first }
            if (atbs.any { pixel.startsWith(it) }) {
                removeAllQueryParameters(AppUrl.ParamKey.ATB)
            }
            if (versions.any { pixel.startsWith(it) }) {
                removeAllQueryParameters(Pixel.PixelParameter.APP_VERSION)
            }
        }.build()

        return chain.proceed(request.url(url).build())
    }

    override fun getInterceptor(): Interceptor {
        return this
    }
}

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PixelParamRemovalPlugin::class,
)
object PixelInterceptorPixelsRequiringDataCleaning : PixelParamRemovalPlugin {
    override fun names(): List<Pair<String, Set<PixelParameter>>> {
        return listOf(
            AppPixelName.EMAIL_COPIED_TO_CLIPBOARD.pixelName to PixelParameter.removeAll(),
            WebViewPixelName.WEB_PAGE_LOADED.pixelName to PixelParameter.removeAll(),
            WebViewPixelName.WEB_PAGE_PAINTED.pixelName to PixelParameter.removeAll(),
            AppPixelName.REFERRAL_INSTALL_UTM_CAMPAIGN.pixelName to PixelParameter.removeAtb(),
            HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_400_DAILY.pixelName to PixelParameter.removeAtb(),
            HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_4XX_DAILY.pixelName to PixelParameter.removeAtb(),
            HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_5XX_DAILY.pixelName to PixelParameter.removeAtb(),
            SitePermissionsPixelName.PERMISSION_DIALOG_CLICK.pixelName to PixelParameter.removeAtb(),
            SitePermissionsPixelName.PERMISSION_DIALOG_IMPRESSION.pixelName to PixelParameter.removeAtb(),
            SITE_NOT_WORKING_SHOWN.pixelName to PixelParameter.removeAtb(),
            SITE_NOT_WORKING_WEBSITE_BROKEN.pixelName to PixelParameter.removeAtb(),
            AppPixelName.APP_VERSION_AT_SEARCH_TIME.pixelName to PixelParameter.removeAll(),
            AppPixelName.MALICIOUS_SITE_PROTECTION_SETTING_TOGGLED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.MALICIOUS_SITE_PROTECTION_VISIT_SITE.pixelName to PixelParameter.removeAtb(),
            AppPixelName.MALICIOUS_SITE_PROTECTION_ERROR_SHOWN.pixelName to PixelParameter.removeAtb(),
            AppPixelName.SET_AS_DEFAULT_SYSTEM_DIALOG_IMPRESSION.pixelName to PixelParameter.removeAtb(),
            AppPixelName.SET_AS_DEFAULT_SYSTEM_DIALOG_CLICK.pixelName to PixelParameter.removeAtb(),
            AppPixelName.SET_AS_DEFAULT_SYSTEM_DIALOG_DISMISSED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.SET_AS_DEFAULT_PROMPT_IMPRESSION.pixelName to PixelParameter.removeAtb(),
            AppPixelName.SET_AS_DEFAULT_PROMPT_CLICK.pixelName to PixelParameter.removeAtb(),
            AppPixelName.SET_AS_DEFAULT_PROMPT_DISMISSED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.SET_AS_DEFAULT_IN_MENU_CLICK.pixelName to PixelParameter.removeAtb(),
            AppPixelName.SET_AS_DEFAULT_MESSAGE_IMPRESSION.pixelName to PixelParameter.removeAtb(),
            AppPixelName.SET_AS_DEFAULT_MESSAGE_CLICK.pixelName to PixelParameter.removeAtb(),
            AppPixelName.SET_AS_DEFAULT_MESSAGE_DISMISSED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.MENU_ACTION_NEW_TAB_PRESSED_FROM_SITE.pixelName to PixelParameter.removeAll(),
            AppPixelName.MENU_ACTION_NEW_TAB_PRESSED_FROM_SERP.pixelName to PixelParameter.removeAll(),
            AppPixelName.SETTINGS_SYNC_PRESSED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.SETTINGS_PASSWORDS_PRESSED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.SETTINGS_EMAIL_PROTECTION_PRESSED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.ONBOARDING_DAX_CTA_DISMISS_BUTTON.pixelName to PixelParameter.removeAtb(),
            AppPixelName.TAB_MANAGER_INFO_PANEL_IMPRESSIONS.pixelName to PixelParameter.removeAll(),
            AppPixelName.TAB_MANAGER_INFO_PANEL_DISMISSED.pixelName to PixelParameter.removeAll(),
            AppPixelName.TAB_MANAGER_INFO_PANEL_TAPPED.pixelName to PixelParameter.removeAll(),
            AppPixelName.PREONBOARDING_INTRO_REINSTALL_USER_SHOWN_UNIQUE.pixelName to PixelParameter.removeAtb(),
            AppPixelName.PREONBOARDING_SKIP_ONBOARDING_SHOWN_UNIQUE.pixelName to PixelParameter.removeAtb(),
            AppPixelName.PREONBOARDING_SKIP_ONBOARDING_PRESSED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.PREONBOARDING_CONFIRM_SKIP_ONBOARDING_PRESSED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.PREONBOARDING_RESUME_ONBOARDING_PRESSED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.SEARCH_AND_FAVORITES_WIDGET_ADDED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.SEARCH_AND_FAVORITES_WIDGET_DELETED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.SEARCH_WIDGET_ADDED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.SEARCH_WIDGET_DELETED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.SETTINGS_APPEARANCE_IS_TRACKER_COUNT_IN_TAB_SWITCHER_TOGGLED.pixelName to PixelParameter.removeAll(),
        )
    }
}
