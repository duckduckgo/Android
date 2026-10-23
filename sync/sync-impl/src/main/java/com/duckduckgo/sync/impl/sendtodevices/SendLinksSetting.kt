/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.sync.impl.sendtodevices

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.notification.checkPermissionAndNotify
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.SYNC_NOTIFICATION_CHANNEL_ID
import com.duckduckgo.sync.api.SyncState.IN_PROGRESS
import com.duckduckgo.sync.api.SyncStateMonitor
import com.duckduckgo.sync.impl.ConnectedDevice
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.settings.api.PrefixSyncableSetting
import com.duckduckgo.sync.settings.api.SyncSettingsListener
import com.duckduckgo.sync.settings.api.SyncableSetting
import com.duckduckgo.sync.store.SyncStore
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import logcat.logcat
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject

@SingleIn(AppScope::class)
@ContributesIntoSet(scope = AppScope::class, binding = binding<SyncableSetting>())
class SendLinksSetting @Inject constructor(
    private val context: Context,
    private val syncStore: SyncStore,
    private val syncFeature: SyncFeature,
    private val syncSettingsListener: SyncSettingsListener,
    // Lazy breaks the cycle SyncAccountRepository -> SyncEngine -> settings plugins -> this
    private val syncAccountRepository: Lazy<SyncAccountRepository>,
    private val syncStateMonitor: SyncStateMonitor,
    private val pinger: SendLinksPinger,
    private val inAppPrompt: SendLinksInAppPrompt,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : SyncableSetting, PrefixSyncableSetting {

    private val preferences: SharedPreferences by lazy { context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE) }

    @Volatile
    private var knownDevices: List<ConnectedDevice> = emptyList()

    override val key: String
        get() = SENT_LINKS_KEY_PREFIX + syncStore.deviceId.orEmpty()

    override val keyPrefix: String = SENT_LINKS_KEY_PREFIX

    override fun isEnabled(): Boolean = syncFeature.sendToDevices().isEnabled() && !syncStore.deviceId.isNullOrEmpty()

    // Re-derived on read, so a multi-link value stored by an earlier build still fits the server's size limit.
    override fun getValue(): String? = parseSentLinks(preferences.getString(KEY_OUTGOING, null))?.links?.lastOrNull()?.asPayload()?.toJson()

    // This device is the only writer of its own key, so the local copy always wins.
    override fun save(value: String?): Boolean = false

    override fun deduplicate(value: String?): Boolean = false

    override fun registerToRemoteChanges(onDataChanged: () -> Unit) = Unit

    override fun onSettingChanged() {
        syncSettingsListener.onSettingChanged(key)
    }

    override fun onSyncDisabled() {
        preferences.edit { remove(KEY_OUTGOING) }
    }

    override fun onRemoteValue(key: String, value: String?) {
        val myId = syncStore.deviceId ?: return
        val senderId = key.removePrefix(SENT_LINKS_KEY_PREFIX)
        if (senderId == myId) return
        val delivered = preferences.getStringSet(KEY_DELIVERED, emptySet()).orEmpty()
        val incoming = parseSentLinks(value)?.linksFor(myId, delivered, Instant.now()).orEmpty()
        if (incoming.isEmpty()) return

        logcat { "Sync-SendLinks: ${incoming.size} new link(s) from $senderId" }
        // ponytail: delivered ids grow unbounded (a few bytes per received link); prune by age if it ever matters
        preferences.edit { putStringSet(KEY_DELIVERED, delivered + incoming.map { it.id }) }
        val senderName = deviceName(senderId)
        incoming.forEach { notify(it, senderName) }
    }

    @WorkerThread
    fun otherDevices(): List<ConnectedDevice> {
        val result = syncAccountRepository.value.getConnectedDevices()
        if (result is Result.Success) knownDevices = result.data
        return knownDevices.filterNot { it.thisDevice || it.deviceType.deviceFactor.contains("browser", ignoreCase = true) }
    }

    /**
     * @return false if the link is too long to fit in a synced setting.
     */
    @WorkerThread
    fun send(to: ConnectedDevice, url: String, title: String): Boolean {
        val now = Instant.now()
        val link = SentLink(
            id = UUID.randomUUID().toString(),
            to = to.deviceId,
            url = url,
            title = title.take(MAX_TITLE_CHARS),
            sentAt = now.truncatedTo(ChronoUnit.SECONDS).toString(),
        )
        val payload = link.asPayload() ?: return false
        preferences.edit(commit = true) { putString(KEY_OUTGOING, payload.toJson()) }
        onSettingChanged()
        pingAfterUpload(to.deviceId)
        return true
    }

    // The ping must land after the link is on the server, so wait for the sync that onSettingChanged() starts.
    private fun pingAfterUpload(deviceId: String) {
        appCoroutineScope.launch(dispatchers.io()) {
            withTimeoutOrNull(UPLOAD_TIMEOUT_MS) {
                syncStateMonitor.syncState().dropWhile { it != IN_PROGRESS }.first { it != IN_PROGRESS }
            }
            pinger.ping(deviceId)
        }
    }

    private fun deviceName(deviceId: String): String {
        val device = knownDevices.firstOrNull { it.deviceId == deviceId }
            ?: otherDevices().firstOrNull { it.deviceId == deviceId }
        return device?.deviceName ?: context.getString(R.string.send_to_devices_unknown_device)
    }

    private fun notify(link: SentLink, senderName: String) {
        val uri = Uri.parse(link.url)
        if (uri.scheme != "http" && uri.scheme != "https") return

        val intent = Intent(Intent.ACTION_VIEW, uri).setPackage(context.packageName)
        if (inAppPrompt.show(intent, senderName, link.title.ifBlank { link.url })) return

        val notificationId = link.id.hashCode()
        val pendingIntent = PendingIntent.getActivity(context, notificationId, intent, PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, SYNC_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(com.duckduckgo.mobile.android.R.drawable.notification_logo)
            .setContentTitle(context.getString(R.string.send_to_devices_notification_title, senderName))
            .setContentText(link.title.ifBlank { link.url })
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).checkPermissionAndNotify(context, notificationId, notification)
    }

    companion object {
        private const val FILENAME = "com.duckduckgo.sync.sendlinks"
        private const val KEY_OUTGOING = "KEY_OUTGOING"
        private const val KEY_DELIVERED = "KEY_DELIVERED"
        private const val UPLOAD_TIMEOUT_MS = 15_000L
    }
}
