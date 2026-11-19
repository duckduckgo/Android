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

package com.duckduckgo.autofill.impl.importing.promo

import android.content.Context
import android.content.ContextWrapper
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.AutofillImportLaunchSource
import com.duckduckgo.autofill.api.promotion.BookmarksImportScreenPromotionPlugin
import com.duckduckgo.autofill.api.promotion.BookmarksImportScreenPromotionPlugin.Callback
import com.duckduckgo.autofill.api.promotion.BookmarksImportScreenPromotionPlugin.Companion.PRIORITY_KEY_IMPORT_PASSWORDS_PROMO
import com.duckduckgo.autofill.api.promotion.BookmarksImportScreenPromotionPlugin.ImportResult
import com.duckduckgo.autofill.impl.databinding.ViewPasswordsInBookmarksImportPromotionBinding
import com.duckduckgo.autofill.impl.importing.promo.PasswordsInBookmarksImportPromotionViewModel.Command
import com.duckduckgo.autofill.impl.importing.promo.PasswordsInBookmarksImportPromotionViewModel.Command.DismissHost
import com.duckduckgo.autofill.impl.importing.promo.PasswordsInBookmarksImportPromotionViewModel.Command.LaunchPasswordsImport
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.IMPORT_PROMOTION_CONFIRMED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.IMPORT_PROMOTION_DISPLAYED
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialog
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialog.ImportPasswordsDialog
import com.duckduckgo.codegen.annotations.ContributesViewModel
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.codegen.annotations.PriorityKey
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.android.support.AndroidSupportInjection
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.ViewScope
import dev.zacsweers.metro.ContributesIntoMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesIntoMap(scope = AppScope::class)
@PriorityKey(PRIORITY_KEY_IMPORT_PASSWORDS_PROMO)
class PasswordsInBookmarksImportScreenPromotionPlugin @Inject constructor(
    private val promotion: PasswordsInBookmarksImportPromotion,
) : BookmarksImportScreenPromotionPlugin {

    override suspend fun isEligible(data: ImportResult): Boolean = promotion.canShowPromotion(data.successCount)

    override fun createView(
        context: Context,
        callback: Callback,
    ): View {
        return PasswordsInBookmarksImportPromotionView(context).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            this.callback = callback
        }
    }
}

@InjectWith(ViewScope::class)
class PasswordsInBookmarksImportPromotionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    var callback: Callback? = null

    private val binding: ViewPasswordsInBookmarksImportPromotionBinding by viewBinding()

    private val viewModel: PasswordsInBookmarksImportPromotionViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[PasswordsInBookmarksImportPromotionViewModel::class.java]
    }

    private var job: ConflatedJob = ConflatedJob()

    init {
        binding.promoMessage.onPrimaryButtonClicked { viewModel.importPasswords() }
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        val lifecycleOwner = findViewTreeLifecycleOwner()!!
        hostFragmentManager()?.setFragmentResultListener(IMPORT_RESULT_KEY, lifecycleOwner) { _, result ->
            viewModel.onPasswordsImportFinished(result.getBoolean(ImportPasswordsDialog.KEY_IMPORT_SUCCESS))
        }

        job += viewModel.commands
            .onEach { processCommand(it) }
            .launchIn(lifecycleOwner.lifecycleScope)

        viewModel.recordImpression()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        job.cancel()
        hostFragmentManager()?.clearFragmentResultListener(IMPORT_RESULT_KEY)
    }

    private fun processCommand(command: Command) {
        when (command) {
            LaunchPasswordsImport -> launchPasswordsImport()
            DismissHost -> callback?.dismissHost()
        }
    }

    private fun launchPasswordsImport() {
        val fragmentManager = hostFragmentManager() ?: return
        if (fragmentManager.findFragmentByTag(DIALOG_TAG) != null) return

        ImportFromGooglePasswordsDialog
            .instance(importSource = AutofillImportLaunchSource.BookmarksImportPromo, tabId = DIALOG_TAG)
            .show(fragmentManager, DIALOG_TAG)
    }

    private fun hostFragmentManager(): FragmentManager? = context.findFragmentActivity()?.supportFragmentManager

    private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
        is FragmentActivity -> this
        is ContextWrapper -> baseContext.findFragmentActivity()
        else -> null
    }

    private companion object {
        const val DIALOG_TAG = "PasswordsInBookmarksImportPromotion"
        val IMPORT_RESULT_KEY = ImportPasswordsDialog.resultKey(DIALOG_TAG)
    }
}

@ContributesViewModel(ViewScope::class)
class PasswordsInBookmarksImportPromotionViewModel @Inject constructor(
    private val pixel: Pixel,
) : ViewModel() {
    private val _commands = Channel<Command>()

    val commands = _commands.receiveAsFlow()

    private var actionJob: Job? = null

    fun recordImpression() {
        pixel.fire(IMPORT_PROMOTION_DISPLAYED, pixelParams())
    }

    fun importPasswords() {
        if (actionJob?.isActive == true) return

        actionJob = viewModelScope.launch {
            pixel.fire(IMPORT_PROMOTION_CONFIRMED, pixelParams())
            _commands.send(LaunchPasswordsImport)
        }
    }

    fun onPasswordsImportFinished(isSuccess: Boolean) {
        if (!isSuccess) return

        viewModelScope.launch {
            _commands.send(DismissHost)
        }
    }

    private fun pixelParams() = mapOf(PIXEL_PARAM_SOURCE to PIXEL_SOURCE)

    sealed interface Command {
        data object LaunchPasswordsImport : Command
        data object DismissHost : Command
    }

    private companion object {
        const val PIXEL_PARAM_SOURCE = "source"
        const val PIXEL_SOURCE = "bookmarks_import"
    }
}
