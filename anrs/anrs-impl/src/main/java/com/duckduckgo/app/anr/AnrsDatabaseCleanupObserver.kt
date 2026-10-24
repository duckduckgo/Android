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

package com.duckduckgo.app.anr

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.anrs.store.AnrsDatabase
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Purges ANRs that have been stored for longer than [ANR_EXPIRY_WINDOW_HOURS].
 *
 * Only the most recent ANR is sent as a pixel (see [AnrOfflinePixelSender]); any older ANRs would
 * otherwise remain in the database indefinitely. See https://github.com/duckduckgo/Android/issues/5009
 */
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class AnrsDatabaseCleanupObserver @Inject constructor(
    private val anrsDatabase: AnrsDatabase,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        appScope.launch(dispatcherProvider.io()) {
            val removeBeforeTimestamp = DatabaseDateFormatter.timestamp(LocalDateTime.now().minusHours(ANR_EXPIRY_WINDOW_HOURS))
            logcat { "Purging ANRs older than $removeBeforeTimestamp" }
            anrsDatabase.arnDao().removeOldAnrs(removeBeforeTimestamp)
        }
    }

    companion object {
        internal const val ANR_EXPIRY_WINDOW_HOURS = 3L
    }
}
