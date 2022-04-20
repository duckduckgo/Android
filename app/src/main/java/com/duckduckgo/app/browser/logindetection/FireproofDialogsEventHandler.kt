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

package com.duckduckgo.app.browser.logindetection

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.duckduckgo.app.browser.logindetection.FireproofDialogsEventHandler.Event
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.events.db.UserEventKey
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.settings.db.SettingsSharedPreferences.LoginDetectorPrefsMapper.AutomaticFireproofSetting
import com.duckduckgo.app.statistics.pixels.Pixel
import kotlinx.coroutines.withContext

interface FireproofDialogsEventHandler {
    val event: LiveData<Event>
    suspend fun onFireproofLoginDialogShown()
    suspend fun onUserConfirmedFireproofDialog(domain: String)
    suspend fun onUserDismissedFireproofLoginDialog()
    suspend fun onDisableLoginDetectionDialogShown()
    suspend fun onUserConfirmedDisableLoginDetectionDialog()
    suspend fun onUserDismissedDisableLoginDetectionDialog()
    suspend fun onUserEnabledAutomaticFireproofLoginDialog(domain: String)
    suspend fun onUserFireproofSiteAutomaticFireproofLoginDialog(domain: String)
    suspend fun onUserDismissedAutomaticFireproofLoginDialog()

    sealed class Event {
        data class FireproofWebSiteSuccess(val fireproofWebsiteEntity: FireproofWebsiteEntity) : Event()
        object AskToDisableLoginDetection : Event()
    }
}

class BrowserTabFireproofDialogsEventHandler(
    private val userEventsStore: UserEventsStore,
    private val pixel: Pixel,
    private val fireproofWebsiteRepository: FireproofWebsiteRepository,
    private val appSettingsPreferencesStore: SettingsDataStore,
    private val dispatchers: DispatcherProvider
) : FireproofDialogsEventHandler {

    override val event: MutableLiveData<Event> = MutableLiveData()

    override suspend fun onFireproofLoginDialogShown() {
        pixel.fire(
            pixel = AppPixelName.FIREPROOF_LOGIN_DIALOG_SHOWN,
            parameters = mapOf(Pixel.PixelParameter.FIRE_EXECUTED to userTriedFireButton().toString())
        )
    }

    override suspend fun onUserConfirmedFireproofDialog(domain: String) {
        withContext(dispatchers.io()) {
            userEventsStore.removeUserEvent(UserEventKey.FIREPROOF_LOGIN_DIALOG_DISMISSED)
            fireproofWebsiteRepository.fireproofWebsite(domain)?.let {
                pixel.fire(AppPixelName.FIREPROOF_WEBSITE_LOGIN_ADDED, mapOf(Pixel.PixelParameter.FIRE_EXECUTED to userTriedFireButton().toString()))
                emitEvent(Event.FireproofWebSiteSuccess(fireproofWebsiteEntity = it))
            }
        }
    }

    override suspend fun onUserDismissedFireproofLoginDialog() {
        pixel.fire(AppPixelName.FIREPROOF_WEBSITE_LOGIN_DISMISS, mapOf(Pixel.PixelParameter.FIRE_EXECUTED to userTriedFireButton().toString()))
        if (allowUserToDisableFireproofLoginActive()) {
            if (shouldAskToDisableFireproofLogin()) {
                event.value = Event.AskToDisableLoginDetection
            } else {
                userEventsStore.registerUserEvent(UserEventKey.FIREPROOF_LOGIN_DIALOG_DISMISSED)
            }
        }
    }

    override suspend fun onDisableLoginDetectionDialogShown() {
        pixel.fire(
            AppPixelName.FIREPROOF_LOGIN_DISABLE_DIALOG_SHOWN,
            mapOf(Pixel.PixelParameter.FIRE_EXECUTED to userTriedFireButton().toString())
        )
    }

    override suspend fun onUserConfirmedDisableLoginDetectionDialog() {
        appSettingsPreferencesStore.automaticFireproofSetting = AutomaticFireproofSetting.NEVER
        pixel.fire(
            AppPixelName.FIREPROOF_LOGIN_DISABLE_DIALOG_DISABLE,
            mapOf(Pixel.PixelParameter.FIRE_EXECUTED to userTriedFireButton().toString())
        )
    }

    override suspend fun onUserDismissedDisableLoginDetectionDialog() {
        appSettingsPreferencesStore.automaticFireproofSetting = AutomaticFireproofSetting.ASK_EVERY_TIME
        userEventsStore.removeUserEvent(UserEventKey.FIREPROOF_LOGIN_DIALOG_DISMISSED)
        userEventsStore.registerUserEvent(UserEventKey.FIREPROOF_DISABLE_DIALOG_DISMISSED)
        pixel.fire(AppPixelName.FIREPROOF_LOGIN_DISABLE_DIALOG_CANCEL, mapOf(Pixel.PixelParameter.FIRE_EXECUTED to userTriedFireButton().toString()))
    }

    override suspend fun onUserEnabledAutomaticFireproofLoginDialog(domain: String) {
        appSettingsPreferencesStore.automaticFireproofSetting = AutomaticFireproofSetting.ALWAYS
        appSettingsPreferencesStore.showAutomaticFireproofDialog = false
        withContext(dispatchers.io()) {
            fireproofWebsiteRepository.fireproofWebsite(domain)?.let {
                pixel.fire(
                    AppPixelName.FIREPROOF_AUTOMATIC_DIALOG_ALWAYS,
                    mapOf(Pixel.PixelParameter.FIRE_EXECUTED to userTriedFireButton().toString())
                )
                emitEvent(Event.FireproofWebSiteSuccess(fireproofWebsiteEntity = it))
            }
        }
    }

    override suspend fun onUserFireproofSiteAutomaticFireproofLoginDialog(domain: String) {
        appSettingsPreferencesStore.automaticFireproofSetting = AutomaticFireproofSetting.ASK_EVERY_TIME
        appSettingsPreferencesStore.showAutomaticFireproofDialog = false
        withContext(dispatchers.io()) {
            fireproofWebsiteRepository.fireproofWebsite(domain)?.let {
                pixel.fire(
                    AppPixelName.FIREPROOF_AUTOMATIC_DIALOG_FIREPROOF_SITE,
                    mapOf(Pixel.PixelParameter.FIRE_EXECUTED to userTriedFireButton().toString())
                )
                emitEvent(Event.FireproofWebSiteSuccess(fireproofWebsiteEntity = it))
            }
        }
    }

    override suspend fun onUserDismissedAutomaticFireproofLoginDialog() {
        appSettingsPreferencesStore.automaticFireproofSetting = AutomaticFireproofSetting.ASK_EVERY_TIME
        appSettingsPreferencesStore.showAutomaticFireproofDialog = false
        pixel.fire(AppPixelName.FIREPROOF_AUTOMATIC_DIALOG_NOT_NOW, mapOf(Pixel.PixelParameter.FIRE_EXECUTED to userTriedFireButton().toString()))
    }

    private suspend fun userTriedFireButton() = userEventsStore.getUserEvent(UserEventKey.FIRE_BUTTON_EXECUTED) != null

    @Suppress("UnnecessaryVariable")
    private suspend fun allowUserToDisableFireproofLoginActive(): Boolean {
        val userEnabledLoginDetection = userEventsStore.getUserEvent(UserEventKey.USER_ENABLED_FIREPROOF_LOGIN) != null
        val userDismissedDisableFireproofLoginDialog = userEventsStore.getUserEvent(UserEventKey.FIREPROOF_DISABLE_DIALOG_DISMISSED) != null
        if (userEnabledLoginDetection || userDismissedDisableFireproofLoginDialog) return false

        return true
    }

    @Suppress("UnnecessaryVariable")
    private suspend fun shouldAskToDisableFireproofLogin(): Boolean {
        val userDismissedDialogBefore = userEventsStore.getUserEvent(UserEventKey.FIREPROOF_LOGIN_DIALOG_DISMISSED) != null
        return userDismissedDialogBefore
    }

    private suspend fun emitEvent(ev: Event) {
        withContext(dispatchers.main()) {
            event.value = ev
        }
    }
}
