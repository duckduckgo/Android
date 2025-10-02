/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.autofill.impl.importing.promo

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
import com.duckduckgo.autofill.api.promotion.PasswordsScreenPromotionPlugin
import com.duckduckgo.autofill.api.promotion.PasswordsScreenPromotionPlugin.Callback
import com.duckduckgo.autofill.api.promotion.PasswordsScreenPromotionPlugin.Companion.PRIORITY_KEY_IMPORT_PROMO
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.ViewImportPasswordsPromoBinding
import com.duckduckgo.autofill.impl.importing.promo.ImportInPasswordsPromotionViewModel.Command
import com.duckduckgo.autofill.impl.importing.promo.ImportInPasswordsPromotionViewModel.Command.DismissImport
import com.duckduckgo.common.ui.view.MessageCta.Message
import com.duckduckgo.common.ui.view.MessageCta.MessageType.REMOTE_MESSAGE
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(scope = AppScope::class)
@PriorityKey(PRIORITY_KEY_IMPORT_PROMO)
class ImportInPasswordsPromotion @Inject constructor(
    private val importInPasswordsVisibility: ImportInPasswordsVisibility,
) : PasswordsScreenPromotionPlugin {

    override suspend fun getView(
        context: Context,
        numberSavedPasswords: Int,
    ): View? {
        if (importInPasswordsVisibility.canShowImportInPasswords(numberSavedPasswords).not()) return null
        logcat { "Autofill: returning view for ImportInPasswordsPromotion" }
        return ImportInPasswordsPromotionView(context)
    }
}

@InjectWith(ViewScope::class)
class ImportInPasswordsPromotionView @JvmOverloads constructor(
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

    private val binding: ViewImportPasswordsPromoBinding by viewBinding()

    private val viewModel: ImportInPasswordsPromotionViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[ImportInPasswordsPromotionViewModel::class.java]
    }

    private var job: ConflatedJob = ConflatedJob()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        job += viewModel.commands()
            .onEach { processCommand(it) }
            .launchIn(findViewTreeLifecycleOwner()?.lifecycleScope!!)

        showPromo()
        viewModel.onPromoShown()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        job.cancel()
    }

    private fun processCommand(command: Command) {
        when (command) {
            DismissImport -> notifyPromoDismissed()
        }
    }

    private fun notifyPromoDismissed() {
        (context as? Callback)?.onPromotionDismissed()
    }

    private fun showPromo() {
        with(binding.importPromo) {
            setMessage(
                Message(
                    topAnimation = R.raw.anim_password_keys,
                    title = context.getString(R.string.passwords_import_promo_title),
                    subtitle = context.getString(R.string.passwords_import_promo_subtitle),
                    action = context.getString(R.string.passwords_import_promo_action),
                    messageType = REMOTE_MESSAGE,
                ),
            )
            onTopAnimationConfigured { view ->
                view.repeatCount = 1
                view.playAnimation()
            }
            onPrimaryActionClicked {
                viewModel.onUserClickedToImport()
            }
            onCloseButtonClicked {
                viewModel.onUserDismissedPromo()
            }
            show()
        }
    }
}
