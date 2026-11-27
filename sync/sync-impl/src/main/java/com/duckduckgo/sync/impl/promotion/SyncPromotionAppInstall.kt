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

package com.duckduckgo.sync.impl.promotion

import com.duckduckgo.browser.api.install.AppInstall
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration

/**
 * App install information as seen by Sync promotions. We keep it separate from [AppInstall] so internal
 * builds can override the installation date for promotions without affecting the rest of the app.
 */
interface SyncPromotionAppInstall {
    suspend fun getInstallAge(): Duration?
}

@ContributesBinding(AppScope::class)
class RealSyncPromotionAppInstall @Inject constructor(
    private val appInstall: AppInstall,
) : SyncPromotionAppInstall {

    override suspend fun getInstallAge(): Duration? = appInstall.getInstallAge()

    fun getInstallationTime(): Instant = Instant.ofEpochMilli(appInstall.getInstallationTimestamp())
}
