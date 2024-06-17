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

package com.duckduckgo.app.browser.cookies.thirdpartycookienames.store

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface ThirdPartyCookieNamesSettingsRepository {
    fun updateAllSettings(settings: ThirdPartyCookieNamesSettings)
    val cookieNames: CopyOnWriteArrayList<String>
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealThirdPartyCookieNamesSettingsRepository @Inject constructor(
    @AppCoroutineScope coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val thirdPartyCookieNamesDao: ThirdPartyCookieNamesDao,
    @IsMainProcess isMainProcess: Boolean,
) : ThirdPartyCookieNamesSettingsRepository {

    override val cookieNames = CopyOnWriteArrayList<String>()

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            if (isMainProcess) {
                loadToMemory()
            }
        }
    }

    override fun updateAllSettings(settings: ThirdPartyCookieNamesSettings) {
        thirdPartyCookieNamesDao.updateAllCookieNames(settings.cookieNames.map { CookieNamesEntity(it) })
        loadToMemory()
    }

    private fun loadToMemory() {
        cookieNames.clear()
        val cookieNamesEntityList = thirdPartyCookieNamesDao.getCookieNames()
        cookieNames.addAll(cookieNamesEntityList.map { it.name })
    }
}
