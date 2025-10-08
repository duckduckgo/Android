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

package com.duckduckgo.autofill.impl.ui.credential.management.survey

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.autofill.api.promotion.PasswordsScreenPromotionPlugin
import com.duckduckgo.autofill.api.promotion.PasswordsScreenPromotionPlugin.Callback
import com.duckduckgo.autofill.api.promotion.PasswordsScreenPromotionPlugin.Companion.PRIORITY_KEY_SURVEY
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.ViewPasswordSurveyBinding
import com.duckduckgo.autofill.impl.ui.credential.management.survey.SurveyInPasswordsPromotionViewModel.Command
import com.duckduckgo.autofill.impl.ui.credential.management.survey.SurveyInPasswordsPromotionViewModel.Command.DismissSurvey
import com.duckduckgo.autofill.impl.ui.credential.management.survey.SurveyInPasswordsPromotionViewModel.Command.LaunchSurvey
import com.duckduckgo.common.ui.view.MessageCta.Message
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@ContributesMultibinding(scope = AppScope::class)
@PriorityKey(PRIORITY_KEY_SURVEY)
class SurveyInPasswordsPromotion @Inject constructor(
    private val autofillSurvey: AutofillSurvey,
) : PasswordsScreenPromotionPlugin {

    override suspend fun getView(context: Context, numberSavedPasswords: Int): View? {
        val survey = autofillSurvey.firstUnusedSurvey() ?: return null
        return SurveyInPasswordsPromotionView(context).also {
            it.survey = survey
            it.tag = survey.id
        }
    }
}

@InjectWith(ViewScope::class)
class SurveyInPasswordsPromotionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var browserNav: BrowserNav

    private val binding: ViewPasswordSurveyBinding by viewBinding()

    private val viewModel: SurveyInPasswordsPromotionViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[SurveyInPasswordsPromotionViewModel::class.java]
    }

    private var job: ConflatedJob = ConflatedJob()
    internal lateinit var survey: SurveyDetails

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        job += viewModel.commands()
            .onEach { processCommand(it) }
            .launchIn(findViewTreeLifecycleOwner()?.lifecycleScope!!)

        showSurvey(survey)

        viewModel.onPromoShown()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        job.cancel()
    }

    private fun processCommand(command: Command) {
        when (command) {
            is LaunchSurvey -> launchSurvey(command)
            DismissSurvey -> notifyPromoDismissed()
        }
    }

    private fun launchSurvey(survey: LaunchSurvey) {
        context.startActivity(browserNav.openInNewTab(context, survey.surveyUrl))
    }

    private fun notifyPromoDismissed() {
        (context as? Callback)?.onPromotionDismissed()
    }

    private fun showSurvey(survey: SurveyDetails) {
        with(binding.autofillSurvey) {
            setMessage(
                Message(
                    topIllustration = R.drawable.ic_passwords_ddg_96,
                    title = context.getString(R.string.autofillManagementSurveyPromptTitle),
                    subtitle = context.getString(R.string.autofillManagementSurveyPromptMessage),
                    action = context.getString(R.string.autofillManagementSurveyPromptAcceptButtonText),
                ),
            )
            onPrimaryActionClicked {
                viewModel.onUserChoseToOpenSurvey(survey)
            }
            onCloseButtonClicked {
                viewModel.onSurveyPromptDismissed(survey.id)
            }
            show()
        }
    }
}
