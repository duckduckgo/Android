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

package com.duckduckgo.app.email.db

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import java.io.IOException
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class EmailEncryptedSharedPreferences(
    private val context: Context,
    private val pixel: Pixel,
    private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
) : EmailDataStore {

    private val mutex: Mutex = Mutex()
    private val encryptedPreferencesDeferred: Deferred<SharedPreferences?> by lazy {
        appCoroutineScope.async(dispatcherProvider.io()) {
            encryptedPreferencesAsync()
        }
    }

    private val encryptedPreferencesSync: SharedPreferences? by lazy { encryptedPreferencesSync() }

    private suspend fun getEncryptedPreferences(): SharedPreferences? {
        return withContext(dispatcherProvider.io()) {
            if (androidBrowserConfigFeature.createAsyncEmailPreferences().isEnabled()) encryptedPreferencesDeferred.await() else encryptedPreferencesSync
        }
    }

    private suspend fun encryptedPreferencesAsync(): SharedPreferences? {
        return mutex.withLock {
            innerEncryptedSharedPreferences()
        }
    }

    @Synchronized
    private fun encryptedPreferencesSync(): SharedPreferences? {
        return innerEncryptedSharedPreferences()
    }

    private fun innerEncryptedSharedPreferences(): SharedPreferences? {
        try {
            return EncryptedSharedPreferences.create(
                context,
                FILENAME,
                MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (t: Throwable) {
            when (t) {
                is AEADBadTagException -> {
                    val recoverable = canInitialiseEncryptedPreferencesTestFile()
                    pixel.enqueueFire(
                        AppPixelName.ENCRYPTION_UNABLE_TO_DECRYPT_SECURE_EMAIL_DATA,
                        mapOf(PIXEL_RECOVERABLE_KEY to recoverable.toString()),
                    )
                }

                is IOException -> pixel.enqueueFire(AppPixelName.ENCRYPTED_IO_EXCEPTION)
                is GeneralSecurityException -> pixel.enqueueFire(AppPixelName.ENCRYPTED_GENERAL_EXCEPTION)
                else -> { /* noop */ }
            }
        }
        return null
    }

    override suspend fun getEmailToken(): String? {
        return withContext(dispatcherProvider.io()) {
            getEncryptedPreferences()?.getString(KEY_EMAIL_TOKEN, null)
        }
    }

    override suspend fun setEmailToken(value: String?) {
        withContext(dispatcherProvider.io()) {
            getEncryptedPreferences()?.edit(commit = true) {
                if (value == null) {
                    remove(KEY_EMAIL_TOKEN)
                } else {
                    putString(KEY_EMAIL_TOKEN, value)
                }
            }
        }
    }

    override suspend fun getNextAlias(): String? {
        return withContext(dispatcherProvider.io()) {
            getEncryptedPreferences()?.getString(KEY_NEXT_ALIAS, null)
        }
    }

    override suspend fun setNextAlias(value: String?) {
        withContext(dispatcherProvider.io()) {
            getEncryptedPreferences()?.edit(commit = true) {
                if (value == null) {
                    remove(KEY_NEXT_ALIAS)
                } else {
                    putString(KEY_NEXT_ALIAS, value)
                }
            }
        }
    }

    override suspend fun getEmailUsername(): String? {
        return withContext(dispatcherProvider.io()) {
            getEncryptedPreferences()?.getString(KEY_EMAIL_USERNAME, null)
        }
    }

    override suspend fun setEmailUsername(value: String?) {
        withContext(dispatcherProvider.io()) {
            getEncryptedPreferences()?.edit(commit = true) {
                if (value == null) {
                    remove(KEY_EMAIL_USERNAME)
                } else {
                    putString(KEY_EMAIL_USERNAME, value)
                }
            }
        }
    }

    override suspend fun getCohort(): String? {
        return withContext(dispatcherProvider.io()) {
            getEncryptedPreferences()?.getString(KEY_COHORT, null)
        }
    }

    override suspend fun setCohort(value: String?) {
        withContext(dispatcherProvider.io()) {
            getEncryptedPreferences()?.edit(commit = true) {
                if (value == null) {
                    remove(KEY_COHORT)
                } else {
                    putString(KEY_COHORT, value)
                }
            }
        }
    }
    override suspend fun getLastUsedDate(): String? {
        return withContext(dispatcherProvider.io()) {
            getEncryptedPreferences()?.getString(KEY_LAST_USED_DATE, null)
        }
    }

    override suspend fun setLastUsedDate(value: String?) {
        withContext(dispatcherProvider.io()) {
            getEncryptedPreferences()?.edit(commit = true) {
                if (value == null) {
                    remove(KEY_LAST_USED_DATE)
                } else {
                    putString(KEY_LAST_USED_DATE, value)
                }
            }
        }
    }

    override suspend fun canUseEncryption(): Boolean = getEncryptedPreferences() != null

    private fun canInitialiseEncryptedPreferencesTestFile(): Boolean {
        return kotlin.runCatching {
            EncryptedSharedPreferences.create(
                context,
                TEST_FILENAME,
                MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            return true.also {
                context.deleteSharedPreferences(TEST_FILENAME)
            }
        }.getOrElse { false }
    }

    companion object {
        const val FILENAME = "com.duckduckgo.app.email.settings"
        const val KEY_EMAIL_TOKEN = "KEY_EMAIL_TOKEN"
        const val KEY_EMAIL_USERNAME = "KEY_EMAIL_USERNAME"
        const val KEY_NEXT_ALIAS = "KEY_NEXT_ALIAS"
        const val KEY_COHORT = "KEY_COHORT"
        const val KEY_LAST_USED_DATE = "KEY_LAST_USED_DATE"
        private const val TEST_FILENAME = "com.duckduckgo.app.email.settings.encryptiontest"
        private const val PIXEL_RECOVERABLE_KEY = "recoverable"
    }
}
