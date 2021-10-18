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

package com.duckduckgo.mobile.android.vpn.waitlist.store

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixelNames
import com.duckduckgo.mobile.android.vpn.waitlist.AppTrackingProtectionWaitlistDataStore
import java.io.IOException
import java.security.GeneralSecurityException

class AppTrackingProtectionEncryptedSharedPreferences(
    private val context: Context,
    private val pixel: Pixel
) : AppTrackingProtectionWaitlistDataStore {

    private val encryptedPreferences: SharedPreferences? = encryptedPreferences()

    @Synchronized
    private fun encryptedPreferences(): SharedPreferences? {
        try {
            return EncryptedSharedPreferences.create(
                context,
                FILENAME,
                MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: IOException) {
            pixel.enqueueFire(DeviceShieldPixelNames.ENCRYPTED_IO_EXCEPTION)
        } catch (e: GeneralSecurityException) {
            pixel.enqueueFire(DeviceShieldPixelNames.ENCRYPTED_GENERAL_EXCEPTION)
        }
        return null
    }

    override var waitlistTimestamp: Int
        get() = encryptedPreferences?.getInt(KEY_WAITLIST_TIMESTAMP, -1) ?: -1
        set(value) {
            encryptedPreferences?.edit(commit = true) {
                putInt(KEY_WAITLIST_TIMESTAMP, value)
            }
        }

    override var waitlistToken: String?
        get() = encryptedPreferences?.getString(KEY_WAITLIST_TOKEN, null)
        set(value) {
            encryptedPreferences?.edit(commit = true) {
                if (value == null) remove(KEY_WAITLIST_TOKEN)
                else putString(KEY_WAITLIST_TOKEN, value)
            }
        }

    override var inviteCode: String?
        get() = encryptedPreferences?.getString(KEY_INVITE_CODE, null)
        set(value) {
            encryptedPreferences?.edit(commit = true) {
                if (value == null) remove(KEY_INVITE_CODE)
                else putString(KEY_INVITE_CODE, value)
            }
        }

    override var sendNotification: Boolean
        get() = encryptedPreferences?.getBoolean(KEY_SEND_NOTIFICATION, false) ?: false
        set(value) {
            encryptedPreferences?.edit(commit = true) {
                putBoolean(KEY_SEND_NOTIFICATION, value)
            }
        }

    override var lastUsedDate: String?
        get() = encryptedPreferences?.getString(KEY_LAST_USED_DATE, null)
        set(value) {
            encryptedPreferences?.edit(commit = true) {
                if (value == null) remove(KEY_LAST_USED_DATE)
                else putString(KEY_LAST_USED_DATE, value)
            }
        }

    override fun canUseEncryption(): Boolean = encryptedPreferences != null

    companion object {
        const val FILENAME = "com.duckduckgo.app.apptp.settings"
        const val KEY_APP_TP_TOKEN = "KEY_EMAIL_TOKEN"
        const val KEY_WAITLIST_TIMESTAMP = "KEY_WAITLIST_TIMESTAMP"
        const val KEY_WAITLIST_TOKEN = "KEY_WAITLIST_TOKEN"
        const val KEY_INVITE_CODE = "KEY_INVITE_CODE"
        const val KEY_SEND_NOTIFICATION = "KEY_SEND_NOTIFICATION"
        const val KEY_LAST_USED_DATE = "KEY_LAST_USED_DATE"
    }
}
