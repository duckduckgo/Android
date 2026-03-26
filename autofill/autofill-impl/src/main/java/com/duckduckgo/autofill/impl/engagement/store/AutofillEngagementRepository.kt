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

package com.duckduckgo.autofill.impl.engagement.store

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_ENGAGEMENT_ACTIVE_USER
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_ENGAGEMENT_ENABLED_USER
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_ENGAGEMENT_STACKED_LOGINS
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SERVICE_DISABLED_DAU
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SERVICE_ENABLED_DAU
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_TOGGLED_OFF_SEARCH
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_TOGGLED_ON_SEARCH
import com.duckduckgo.autofill.impl.pixel.AutofillPixelParameters.LAST_USED_PIXEL_KEY
import com.duckduckgo.autofill.impl.securestorage.SecureStorage
import com.duckduckgo.autofill.impl.service.store.AutofillServiceStore
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.autofill.store.AutofillPrefsStore
import com.duckduckgo.autofill.store.engagement.AutofillEngagementDao
import com.duckduckgo.autofill.store.engagement.AutofillEngagementDatabase
import com.duckduckgo.autofill.store.engagement.AutofillEngagementEntity
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import logcat.LogPriority.VERBOSE
import logcat.logcat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

interface AutofillEngagementRepository {

    /**
     * Record that the user has autofilled today
     */
    suspend fun recordAutofilledToday()

    /**
     * Record that the user has searched today
     */
    suspend fun recordSearchedToday()

    /**
     * Clear all data in the database, optionally preserving today's data
     */
    suspend fun clearData(preserveToday: Boolean = true)
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class DefaultAutofillEngagementRepository @Inject constructor(
    engagementDb: AutofillEngagementDatabase,
    private val pixel: Pixel,
    private val autofillStore: InternalAutofillStore,
    private val engagementBucketing: AutofillEngagementBucketing,
    private val dispatchers: DispatcherProvider,
    private val secureStorage: SecureStorage,
    private val deviceAuthenticator: DeviceAuthenticator,
    private val autofillServiceStore: AutofillServiceStore,
    private val autofillPrefsStore: AutofillPrefsStore,
) : AutofillEngagementRepository {

    private val autofillEngagementDao: AutofillEngagementDao = engagementDb.autofillEngagementDao()

    override suspend fun recordAutofilledToday() {
        withContext(dispatchers.io()) {
            val engagement = todaysEngagement().copy(autofilled = true)
            logcat(VERBOSE) { "upserting $engagement because user autofilled" }
            processEvent(engagement)
            autofillPrefsStore.dataLastAutofilledDate = engagement.date
        }
    }

    override suspend fun recordSearchedToday() {
        withContext(dispatchers.io()) {
            val engagement = todaysEngagement().copy(searched = true)
            logcat(VERBOSE) { "upserting $engagement because user searched" }
            processEvent(engagement)
            processOnFirstSearchEvent()
        }
    }

    private suspend fun processOnFirstSearchEvent() {
        if (!canSendAutofillToggleStatus()) {
            logcat(VERBOSE) { "Unable to determine autofill toggle status" }
            return
        }

        val numberStoredPasswords = getNumberStoredPasswords()
        val togglePixel = if (autofillStore.autofillEnabled) AUTOFILL_TOGGLED_ON_SEARCH else AUTOFILL_TOGGLED_OFF_SEARCH
        val bucket = engagementBucketing.bucketNumberOfCredentials(numberStoredPasswords)
        pixel.fire(togglePixel, mapOf("count_bucket" to bucket), type = Daily())

        val autofillServiceStatus = if (autofillServiceStore.isDefaultAutofillProvider()) {
            AUTOFILL_SERVICE_ENABLED_DAU
        } else {
            AUTOFILL_SERVICE_DISABLED_DAU
        }
        pixel.fire(autofillServiceStatus, mapOf("count_bucket" to bucket), type = Daily())
    }

    private suspend fun DefaultAutofillEngagementRepository.processEvent(engagement: AutofillEngagementEntity) {
        autofillEngagementDao.upsert(engagement)
        engagement.sendPixelsIfCriteriaMet()
        clearData()
    }

    private suspend fun AutofillEngagementEntity.sendPixelsIfCriteriaMet() {
        val numberStoredPasswords = getNumberStoredPasswords()
        val lastUsed = autofillPrefsStore.dataLastAutofilledDate

        if (autofilled && searched) {
            logcat { "User autofilled and searched today, sending engagement pixels. lastAutofilled=$lastUsed" }

            val activeUserParams = if (lastUsed == null) emptyMap() else mapOf(LAST_USED_PIXEL_KEY to lastUsed)
            pixel.fire(AUTOFILL_ENGAGEMENT_ACTIVE_USER, parameters = activeUserParams, type = Daily())

            val bucket = engagementBucketing.bucketNumberOfCredentials(numberStoredPasswords)
            pixel.fire(AUTOFILL_ENGAGEMENT_STACKED_LOGINS, mapOf("count_bucket" to bucket), type = Daily())
        }

        if (searched && numberStoredPasswords >= 10 && autofillStore.autofillEnabled) {
            pixel.fire(AUTOFILL_ENGAGEMENT_ENABLED_USER, type = Daily())
        }
    }

    private suspend fun getNumberStoredPasswords(): Int {
        return autofillStore.getCredentialCount().firstOrNull() ?: 0
    }

    override suspend fun clearData(preserveToday: Boolean) {
        if (preserveToday) {
            autofillEngagementDao.deleteOlderThan(todayString())
        } else {
            autofillEngagementDao.deleteAll()
        }
    }

    private suspend fun todaysEngagement(): AutofillEngagementEntity {
        val key = todayString()
        return autofillEngagementDao.getEngagement(key) ?: entityForToday()
    }

    private fun entityForToday(): AutofillEngagementEntity {
        return AutofillEngagementEntity(date = todayString(), autofilled = false, searched = false)
    }

    private fun todayString(): String {
        return DATE_FORMATTER.format(LocalDate.now())
    }

    private suspend fun canSendAutofillToggleStatus(): Boolean {
        return withContext(dispatchers.io()) {
            val secureStorageSupported = secureStorage.canAccessSecureStorage()
            val deviceAuthEnabled = deviceAuthenticator.hasValidDeviceAuthentication()
            secureStorageSupported && deviceAuthEnabled
        }
    }

    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}
