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

package com.duckduckgo.app.quack

import com.duckduckgo.app.browser.newaddressbaroption.NewAddressBarPickerDataStore
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.cta.model.DismissedCta
import com.duckduckgo.app.cta.ui.CtaViewModel
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import dev.zacsweers.metro.ContributesIntoSet
import io.mehow.quack.QuackAction
import io.mehow.quack.QuackAction.Args
import io.mehow.quack.QuackAction.Outcome
import io.mehow.quack.QuackAction.Param
import io.mehow.quack.QuackAction.Terminal
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesIntoSet(AppScope::class)
class CompleteOnboardingQuackAction @Inject constructor(
    private val userStageStore: UserStageStore,
    private val settingsDataStore: SettingsDataStore,
    private val dismissedCtaDao: DismissedCtaDao,
    private val ctaViewModel: CtaViewModel,
    private val newAddressBarPickerDataStore: NewAddressBarPickerDataStore,
    private val duckChat: DuckChat,
    private val dispatchers: DispatcherProvider,
) : QuackAction {
    override val id = "onboarding.complete"
    override val description = "Completes onboarding"
    override val params = emptyList<Param<*>>()

    override suspend fun execute(args: Args, terminal: Terminal): Outcome = withContext(dispatchers.io()) {
        userStageStore.moveToStage(AppStage.ESTABLISHED)
        settingsDataStore.hideTips = false
        onboardingCtaIds().forEach { ctaId -> dismissedCtaDao.insert(DismissedCta(ctaId)) }
        newAddressBarPickerDataStore.setAsShown()
        duckChat.setInputScreenUserSetting(true)
        Outcome.Ok("Onboarding is completed")
    }

    private suspend fun onboardingCtaIds(): List<CtaId> {
        val requiredDialogs = ctaViewModel.requiredDaxOnboardingCtas()
        val extraDialogs = listOf(
            CtaId.DAX_INTRO_VISIT_SITE,
            CtaId.DAX_DIALOG_NETWORK,
            CtaId.DAX_DIALOG_OTHER,
        ).filterNot { it in requiredDialogs }
        return requiredDialogs + extraDialogs
    }
}
