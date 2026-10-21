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
package com.duckduckgo.nextsteps.impl.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.widget.AddWidgetLauncher
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.android.support.AndroidSupportInjection
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.GlobalActivityStarter.DeeplinkActivityParams
import com.duckduckgo.nextsteps.impl.R
import com.duckduckgo.nextsteps.impl.databinding.ViewNextStepsCardBinding
import com.duckduckgo.nextsteps.impl.databinding.ViewNextStepsStackedCardsBinding
import com.duckduckgo.nextsteps.impl.ui.NextStepsItemsViewModel.Command
import com.duckduckgo.nextsteps.impl.ui.NextStepsItemsViewModel.ViewState
import com.duckduckgo.remote.messaging.api.CardItem
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority.WARN
import logcat.logcat
import java.util.UUID
import javax.inject.Inject

@InjectWith(ViewScope::class)
class NextStepsStackedCardsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var addWidgetLauncher: AddWidgetLauncher

    @Inject
    lateinit var defaultRoleBrowserDialog: DefaultRoleBrowserDialog

    private val binding: ViewNextStepsStackedCardsBinding by viewBinding()

    private val viewModel: NextStepsItemsViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[NextStepsItemsViewModel::class.java]
    }

    private val conflatedStateJob = ConflatedJob()
    private val conflatedCommandJob = ConflatedJob()
    private var lifecycleOwner: LifecycleOwner? = null
    private var dismissAnimationRunning = false
    private val defaultBrowserDialogKey = "next_steps_default_browser_${UUID.randomUUID()}"
    private var defaultBrowserDialogLauncher: ActivityResultLauncher<Intent>? = null

    private val backCardOffset = resources.getDimension(R.dimen.nextStepsBackCardOffset)
    private val frontElevation = resources.getDimension(R.dimen.nextStepsCardElevation)
    private val backElevation = resources.getDimension(R.dimen.nextStepsBackCardElevation)

    init {
        binding.frontCard.closeButton.setOnClickListener { dismissFrontCard() }
        binding.frontCard.primaryAction.setOnClickListener { viewModel.onPrimaryActionClicked() }
        binding.backCard.closeButton.isEnabled = false
        binding.backCard.primaryAction.isEnabled = false
        binding.backCard.root.isClickable = false
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        defaultBrowserDialogLauncher = (context.getActivity() as? ComponentActivity)?.activityResultRegistry?.register(
            defaultBrowserDialogKey,
            ActivityResultContracts.StartActivityForResult(),
        ) { }

        val lifecycleOwner = findViewTreeLifecycleOwner()!!.also { this.lifecycleOwner = it }
        lifecycleOwner.lifecycle.addObserver(viewModel)
        viewModel.onViewAttached()
        conflatedStateJob += viewModel.viewState
            .onEach { render(it) }
            .launchIn(lifecycleOwner.lifecycleScope)

        conflatedCommandJob += viewModel.commands()
            .onEach { processCommand(it) }
            .launchIn(lifecycleOwner.lifecycleScope)
    }

    override fun onDetachedFromWindow() {
        binding.frontCard.root.animate().cancel()
        binding.backCard.root.animate().cancel()
        dismissAnimationRunning = false
        conflatedStateJob.cancel()
        conflatedCommandJob.cancel()
        viewModel.onViewDetached()
        defaultBrowserDialogLauncher?.unregister()
        defaultBrowserDialogLauncher = null
        lifecycleOwner?.lifecycle?.removeObserver(viewModel)
        lifecycleOwner = null
        super.onDetachedFromWindow()
    }

    private fun processCommand(command: Command) {
        when (command) {
            is Command.LaunchAddWidget -> addWidgetLauncher.launchAddWidget(context, command.widgetType)
            is Command.LaunchDefaultBrowserDialog -> launchDefaultBrowserDialog()
            is Command.LaunchDefaultBrowserSettings -> launchDefaultBrowserSettings()
            is Command.LaunchScreen -> {
                globalActivityStarter.start(context, DeeplinkActivityParams(screenName = command.screen, jsonArguments = command.payload), null)
            }
            is Command.AnimateFrontCardCompletion -> animateFrontCardOut { viewModel.onFrontCardCompletionAnimated() }
        }
    }

    private fun launchDefaultBrowserDialog() {
        val launcher = defaultBrowserDialogLauncher
        if (launcher == null) {
            launchDefaultBrowserSettings()
            return
        }

        val launched = try {
            val intent = defaultRoleBrowserDialog.createIntent(context)
            if (intent != null) {
                launcher.launch(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            logcat(WARN) { "Can't launch default browser dialog" }
            false
        }

        if (launched) {
            viewModel.onDefaultBrowserDialogShown()
        } else {
            launchDefaultBrowserSettings()
        }
    }

    private fun launchDefaultBrowserSettings() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            intent.putExtra(":settings:fragment_args_key", "default_browser")
            intent.putExtra(":settings:show_fragment_args", bundleOf(":settings:fragment_args_key" to "default_browser"))
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            logcat(WARN) { "Can't launch default app settings screen" }
        }
    }

    private fun Context.getActivity(): Activity? {
        var current = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    private fun render(viewState: ViewState) {
        if (dismissAnimationRunning) return

        val frontItem = viewState.items.firstOrNull()
        if (viewState.message == null || frontItem == null) {
            binding.stack.gone()
            return
        }
        binding.frontCard.bind(viewState.title, frontItem)
        resetStackTransforms()

        val backItem = viewState.items.getOrNull(1)
        if (backItem != null) {
            binding.backCard.bind(viewState.title, backItem)
            binding.backCard.root.show()
        } else {
            binding.backCard.root.gone()
        }
        binding.stack.show()
    }

    private fun dismissFrontCard() {
        animateFrontCardOut { viewModel.onFrontCardDismissed() }
    }

    private fun animateFrontCardOut(onEnd: () -> Unit) {
        if (dismissAnimationRunning) return
        dismissAnimationRunning = true

        val front = binding.frontCard.root
        val back = binding.backCard.root
        val hasBackCard = back.isVisible

        front.animate()
            .translationY(-front.height * FRONT_EXIT_TRANSLATION_FRACTION)
            .alpha(0f)
            .setDuration(FRONT_EXIT_DURATION_MS)
            .withEndAction {
                if (!hasBackCard) finishDismiss(onEnd)
            }
            .start()

        if (hasBackCard) {
            back.animate()
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(BACK_PROMOTE_DURATION_MS)
                .withEndAction { finishDismiss(onEnd) }
                .start()
        }
    }

    private fun finishDismiss(onEnd: () -> Unit) {
        dismissAnimationRunning = false
        onEnd()
    }

    private fun resetStackTransforms() {
        binding.frontCard.root.apply {
            animate().cancel()
            translationY = 0f
            alpha = 1f
            elevation = frontElevation
        }
        binding.backCard.root.apply {
            animate().cancel()
            scaleX = BACK_CARD_SCALE
            scaleY = BACK_CARD_SCALE
            alpha = 1f
            elevation = backElevation
            translationY = backCardOffset
            doOnLayout {
                pivotX = it.width / 2f
                pivotY = it.height.toFloat()
            }
        }
    }

    private fun ViewNextStepsCardBinding.bind(
        title: String,
        item: CardItem.ListItem,
    ) {
        sectionTitle.text = title
        itemTitle.text = item.titleText
        itemDescription.text = item.descriptionText
        primaryAction.text = item.primaryActionText
        if (item.imageUrl.isNullOrEmpty()) {
            Glide.with(itemImage).clear(itemImage)
            itemImage.gone()
        } else {
            itemImage.show()
            Glide.with(itemImage)
                .load(item.imageUrl)
                .fitCenter()
                .into(itemImage)
        }
    }

    companion object {
        private const val BACK_CARD_SCALE = 0.92f
        private const val FRONT_EXIT_TRANSLATION_FRACTION = 0.3f
        private const val FRONT_EXIT_DURATION_MS = 200L
        private const val BACK_PROMOTE_DURATION_MS = 250L
    }
}
