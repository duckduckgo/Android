/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.mobile.android.ui.notifyme

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.ViewNotifyMeViewBinding
import com.duckduckgo.mobile.android.ui.notifyme.NotifyMeViewModel.Command
import com.duckduckgo.mobile.android.ui.notifyme.NotifyMeViewModel.Command.CheckPermissions
import com.duckduckgo.mobile.android.ui.notifyme.NotifyMeViewModel.Command.CheckShouldShowRequestPermissionRationale
import com.duckduckgo.mobile.android.ui.notifyme.NotifyMeViewModel.Command.Close
import com.duckduckgo.mobile.android.ui.notifyme.NotifyMeViewModel.Command.OpenSettings
import com.duckduckgo.mobile.android.ui.notifyme.NotifyMeViewModel.Command.ShowPermissionRationale
import com.duckduckgo.mobile.android.ui.notifyme.NotifyMeViewModel.ViewState
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class NotifyMeView : FrameLayout {

    constructor(context: Context) : this(context, null)

    constructor(
        context: Context,
        attrs: AttributeSet?,
    ) : this(
        context,
        attrs,
        R.style.Widget_DuckDuckGo_NotifyMeView,
    )

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyle: Int,
    ) : super(context, attrs, defStyle) {

        val attributes = context.obtainStyledAttributes(attrs, R.styleable.NotifyMeView)

        setTitle(attributes.getString(R.styleable.NotifyMeView_titleText) ?: "")
        setSubtitle(attributes.getString(R.styleable.NotifyMeView_subtitleText) ?: "")

        binding.notifyMeClose.setOnClickListener {
            viewModel.onCloseButtonClicked()
        }

        binding.notifyMeButton.setOnClickListener {
            viewModel.onNotifyMeButtonClicked()
        }

        attributes.recycle()
    }

    private var listener: NotifyMeListener? = null
    private var coroutineScope: CoroutineScope? = null

    private val binding: ViewNotifyMeViewBinding by viewBinding()

    private val viewModel by lazy {
        val factory = NotifyMeViewModel.Factory(findViewTreeSavedStateRegistryOwner()!!)
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, factory)[NotifyMeViewModel::class.java]
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        ViewTreeLifecycleOwner.get(this)?.lifecycle?.addObserver(viewModel)

        @SuppressLint("NoHardcodedCoroutineDispatcher")
        coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        viewModel.setNotifyMeListener(listener)

        viewModel.viewState
            .onEach { render(it) }
            .launchIn(coroutineScope!!)

        viewModel.commands()
            .onEach { processCommands(it) }
            .launchIn(coroutineScope!!)

        checkNotificationsPermissions()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        ViewTreeLifecycleOwner.get(this)?.lifecycle?.removeObserver(viewModel)

        viewModel.removeNotifyMeListener()

        listener = null

        coroutineScope?.cancel()
        coroutineScope = null
    }

    fun setListener(listener: NotifyMeListener?) {
        this.listener = listener
    }

    fun setTitle(title: String) {
        binding.notifyMeMessageTitle.text = title
    }

    fun setSubtitle(subtitle: String) {
        binding.notifyMeMessageSubtitle.text = subtitle
    }

    private fun render(viewState: ViewState) {
        if (viewState.visible) {
            showMe()
        } else {
            hideMe()
        }
    }

    private fun processCommands(command: Command) {
        when (command) {
            is CheckPermissions -> checkNotificationsPermissions()
            is OpenSettings -> openSettings()
            is Close -> hideMe()
            is CheckShouldShowRequestPermissionRationale -> checkRequestPermissionRationale()
            is ShowPermissionRationale -> showNotificationsPermissionsPrompt()
        }
    }

    private fun checkNotificationsPermissions() {
        if (isAtLeastAndroid13()) {
            val value =
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            viewModel.updateNotificationsPermissions(value)
        } else {
            viewModel.updateNotificationsPermissions(true)
        }
    }

    private fun openSettings() {
        // This gets called only on Android 13+.
        @SuppressLint("InlinedApi")
        val settingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

        startActivity(context, settingsIntent, null)
    }

    private fun showMe() {
        this.show()
    }

    private fun hideMe() {
        this.gone()
    }

    private fun checkRequestPermissionRationale() {
        // This gets called only on Android 13+.
        @SuppressLint("InlinedApi")
        val showPrompt = ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.POST_NOTIFICATIONS)
        if (showPrompt) {
            viewModel.onPermissionRationaleNeeded()
        } else {
            viewModel.onOpenSettings()
        }
    }

    private fun showNotificationsPermissionsPrompt() {
        if (isAtLeastAndroid13()) {
            ActivityCompat.requestPermissions(getActivity(), arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    private fun isAtLeastAndroid13(): Boolean {
        return Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
    }

    private fun getActivity(): Activity {
        // Gross way of unwrapping the Activity. Taken from 'MediaRouteButton.java'.
        var context = context
        while (context is ContextWrapper) {
            if (context is Activity) {
                return context
            }
            context = context.baseContext
        }
        throw IllegalStateException("The NotifyMeView's Context is not an Activity.")
    }
}
