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

package com.duckduckgo.autoconsent.impl.prompt

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.autoconsent.impl.R
import com.duckduckgo.autoconsent.impl.remoteconfig.AutoconsentFeature
import com.duckduckgo.autoconsent.impl.store.AutoconsentSettingsRepository
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.promptscoordinator.api.ModalEvaluator
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = ModalEvaluator::class,
)
@SingleInstanceIn(scope = AppScope::class)
class CookiePopupOptInEvaluator @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val applicationContext: Context,
    private val autoconsentFeature: AutoconsentFeature,
    private val dispatchers: DispatcherProvider,
    private val settingsRepository: AutoconsentSettingsRepository,
) : ModalEvaluator {

    override val priority: Int = 6

    override val evaluatorId: String = "cookie_popup_opt_in"

    override suspend fun evaluate(): ModalEvaluator.EvaluationResult = withContext(dispatchers.io()) {
        val eligible = autoconsentFeature.self().isEnabled() &&
            autoconsentFeature.cookiePopUpPreferenceSetting().isEnabled() &&
            autoconsentFeature.cookiePopUpOptInPrompt().isEnabled() &&
            !settingsRepository.clickAcceptEnabled &&
            !settingsRepository.optInPromptChoiceMade &&
            settingsRepository.optInPromptShownCount < MAX_PROMPT_DISPLAYS

        if (!eligible) {
            return@withContext ModalEvaluator.EvaluationResult.Skipped
        }

        settingsRepository.optInPromptShownCount++

        delay(MODAL_DISPLAY_DELAY)
        appCoroutineScope.launch(dispatchers.main()) {
            val intent = CookiePopupOptInActivity.intent(applicationContext).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val options = ActivityOptions.makeCustomAnimation(applicationContext, R.anim.slide_from_bottom, 0).toBundle()
            applicationContext.startActivity(intent, options)
        }

        return@withContext ModalEvaluator.EvaluationResult.ModalShown
    }

    companion object {
        private const val MODAL_DISPLAY_DELAY = 250L
        private const val MAX_PROMPT_DISPLAYS = 3
    }
}
