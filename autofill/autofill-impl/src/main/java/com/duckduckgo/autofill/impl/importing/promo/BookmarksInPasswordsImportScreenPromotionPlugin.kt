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
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.withResumed
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.ImportFromGoogleLauncher
import com.duckduckgo.autofill.api.ImportFromGoogleLauncher.BookmarksImportRequest
import com.duckduckgo.autofill.api.ImportFromGoogleLauncher.ImportFromGoogleResult
import com.duckduckgo.autofill.api.ImportFromGoogleLauncher.LaunchSource
import com.duckduckgo.autofill.api.promotion.PasswordsImportScreenPromotionPlugin
import com.duckduckgo.autofill.api.promotion.PasswordsImportScreenPromotionPlugin.Callback
import com.duckduckgo.autofill.api.promotion.PasswordsImportScreenPromotionPlugin.Companion.PRIORITY_KEY_IMPORT_BOOKMARKS_PROMO
import com.duckduckgo.autofill.api.promotion.PasswordsImportScreenPromotionPlugin.ImportResult
import com.duckduckgo.autofill.impl.databinding.ViewBookmarksInPasswordsImportPromotionBinding
import com.duckduckgo.autofill.impl.importing.promo.BookmarksInPasswordsImportPromotionViewModel.Command
import com.duckduckgo.autofill.impl.importing.promo.BookmarksInPasswordsImportPromotionViewModel.Command.DismissHost
import com.duckduckgo.autofill.impl.importing.promo.BookmarksInPasswordsImportPromotionViewModel.Command.LaunchBookmarksImport
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.IMPORT_PROMOTION_CONFIRMED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.IMPORT_PROMOTION_DISPLAYED
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ExternalImportAuthSuppressor
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesIntoMap(scope = AppScope::class)
@PriorityKey(PRIORITY_KEY_IMPORT_BOOKMARKS_PROMO)
class BookmarksInPasswordsImportScreenPromotionPlugin @Inject constructor(
    private val promotion: BookmarksInPasswordsImportPromotion,
) : PasswordsImportScreenPromotionPlugin {

    override suspend fun isEligible(data: ImportResult): Boolean = promotion.canShowPromotion(data.successCount)

    override fun createView(
        context: Context,
        callback: Callback,
    ): View {
        return BookmarksInPasswordsImportPromotionView(context).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            this.callback = callback
        }
    }
}

@InjectWith(ViewScope::class)
class BookmarksInPasswordsImportPromotionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    @Inject
    lateinit var importFromGoogleLauncher: ImportFromGoogleLauncher

    @Inject
    lateinit var externalImportAuthSuppressor: ExternalImportAuthSuppressor

    var callback: Callback? = null

    private val binding: ViewBookmarksInPasswordsImportPromotionBinding by viewBinding()

    private val viewModel: BookmarksInPasswordsImportPromotionViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[BookmarksInPasswordsImportPromotionViewModel::class.java]
    }

    private var job: ConflatedJob = ConflatedJob()

    private var googleBookmarksImport: ActivityResultLauncher<BookmarksImportRequest>? = null

    private var pendingImportResult: CompletableDeferred<ImportFromGoogleResult>? = null

    init {
        binding.promoMessage.onPrimaryButtonClicked { viewModel.importBookmarks() }
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        // we register with the activity directly because the host is a dialog fragment that is already created,
        // so it is too late to register for activity results through the fragment
        googleBookmarksImport = context.findComponentActivity()?.activityResultRegistry?.register(
            IMPORT_RESULT_KEY,
            importFromGoogleLauncher.bookmarksImportContract(),
        ) { pendingImportResult?.complete(it) }

        job += viewModel.commands
            .onEach { processCommand(it) }
            .launchIn(findViewTreeLifecycleOwner()!!.lifecycleScope)

        viewModel.recordImpression()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        job.cancel()
        googleBookmarksImport?.unregister()
        googleBookmarksImport = null
    }

    private suspend fun processCommand(command: Command) {
        when (command) {
            LaunchBookmarksImport -> launchBookmarksImport()
            DismissHost -> callback?.dismissHost()
        }
    }

    private suspend fun launchBookmarksImport() {
        val launcher = googleBookmarksImport ?: return
        val importResult = CompletableDeferred<ImportFromGoogleResult>()
        pendingImportResult = importResult

        // the host dismisses on success, which clears the auth grace period before the passwords screen checks it,
        // so we suppress the prompt instead; the passwords screen consumes the suppression as soon as it restarts
        externalImportAuthSuppressor.suppressAuthPromptWhileRunning {
            importFromGoogleLauncher.launchBookmarksImport(LaunchSource.PasswordsImportPromo, launcher)
            val result = importResult.await()
            // the result can arrive before the host restarts; staying in the block until resumed keeps the suppression available to its onStart
            findViewTreeLifecycleOwner()!!.lifecycle.withResumed {
                viewModel.onBookmarksImportFinished(result is ImportFromGoogleResult.Success)
            }
        }
    }

    private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findComponentActivity()
        else -> null
    }

    private companion object {
        const val IMPORT_RESULT_KEY = "BookmarksInPasswordsImportPromotion"
    }
}

@ContributesViewModel(ViewScope::class)
class BookmarksInPasswordsImportPromotionViewModel @Inject constructor(
    private val pixel: Pixel,
) : ViewModel() {
    private val _commands = Channel<Command>()

    val commands = _commands.receiveAsFlow()

    private var actionJob: Job? = null

    fun recordImpression() {
        pixel.fire(IMPORT_PROMOTION_DISPLAYED, pixelParams())
    }

    fun importBookmarks() {
        if (actionJob?.isActive == true) return

        actionJob = viewModelScope.launch {
            pixel.fire(IMPORT_PROMOTION_CONFIRMED, pixelParams())
            _commands.send(LaunchBookmarksImport)
        }
    }

    fun onBookmarksImportFinished(isSuccess: Boolean) {
        if (!isSuccess) return

        viewModelScope.launch {
            _commands.send(DismissHost)
        }
    }

    private fun pixelParams() = mapOf(PIXEL_PARAM_SOURCE to PIXEL_SOURCE)

    sealed interface Command {
        data object LaunchBookmarksImport : Command
        data object DismissHost : Command
    }

    private companion object {
        const val PIXEL_PARAM_SOURCE = "source"
        const val PIXEL_SOURCE = "passwords_import"
    }
}
