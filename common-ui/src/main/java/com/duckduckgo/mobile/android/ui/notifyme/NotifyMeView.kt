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
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.FrameLayout
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.ViewNotifyMeViewBinding
import com.duckduckgo.mobile.android.ui.notifyme.NotifyMeViewModel.Command
import com.duckduckgo.mobile.android.ui.notifyme.NotifyMeViewModel.Command.CheckPermissionRationale
import com.duckduckgo.mobile.android.ui.notifyme.NotifyMeViewModel.Command.DismissComponent
import com.duckduckgo.mobile.android.ui.notifyme.NotifyMeViewModel.Command.OpenSettings
import com.duckduckgo.mobile.android.ui.notifyme.NotifyMeViewModel.Command.OpenSettingsOnAndroid8Plus
import com.duckduckgo.mobile.android.ui.notifyme.NotifyMeViewModel.Command.ShowPermissionRationale
import com.duckduckgo.mobile.android.ui.notifyme.NotifyMeViewModel.Command.UpdateNotificationsState
import com.duckduckgo.mobile.android.ui.notifyme.NotifyMeViewModel.Command.UpdateNotificationsStateOnAndroid13Plus
import com.duckduckgo.mobile.android.ui.notifyme.NotifyMeViewModel.ViewState
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ViewScope::class)
class NotifyMeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: NotifyMeViewModel.Factory

    private lateinit var pixelParentScreenName: String
    private lateinit var sharedPrefsKeyForDismiss: String

    private var coroutineScope: CoroutineScope? = null
    private var vtoGlobalLayoutListener: OnGlobalLayoutListener? = null
    private var visibilityChangedListener: OnVisibilityChangedListener? = null

    private val binding: ViewNotifyMeViewBinding by viewBinding()

    private val viewModel: NotifyMeViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[NotifyMeViewModel::class.java]
    }

    init {
        val attributes = context.obtainStyledAttributes(attrs, R.styleable.NotifyMeView)
        setPrimaryText(attributes.getString(R.styleable.NotifyMeView_primaryText) ?: "")
        setSecondaryText(attributes.getString(R.styleable.NotifyMeView_secondaryText) ?: "")
        setPixelParentScreenName(attributes.getString(R.styleable.NotifyMeView_pixelParentScreenName) ?: "")
        setSharedPrefsKeyForDismiss(attributes.getString(R.styleable.NotifyMeView_sharedPrefsKeyForDismiss) ?: "")
        binding.notifyMeClose.setOnClickListener {
            viewModel.onCloseButtonClicked()
        }
        binding.notifyMeButton.setOnClickListener {
            viewModel.onNotifyMeButtonClicked()
        }
        attributes.recycle()
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        addViewTreeObserverOnGlobalLayoutListener()

        ViewTreeLifecycleOwner.get(this)?.lifecycle?.addObserver(viewModel)

        @SuppressLint("NoHardcodedCoroutineDispatcher")
        coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        viewModel.init(pixelParentScreenName, sharedPrefsKeyForDismiss)

        viewModel.viewState
            .onEach { render(it) }
            .launchIn(coroutineScope!!)

        viewModel.commands()
            .onEach { processCommands(it) }
            .launchIn(coroutineScope!!)

        updateNotificationsState()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        removeViewTreeObserverOnGlobalLayoutListener()

        ViewTreeLifecycleOwner.get(this)?.lifecycle?.removeObserver(viewModel)

        coroutineScope?.cancel()
        coroutineScope = null
    }

    fun setOnVisibilityChange(visibilityChangedListener: OnVisibilityChangedListener) {
        this.visibilityChangedListener = visibilityChangedListener
    }

    fun setPrimaryText(title: String) {
        binding.notifyMeMessageTitle.text = title
    }

    fun setSecondaryText(subtitle: String) {
        binding.notifyMeMessageSubtitle.text = subtitle
    }

    fun setPixelParentScreenName(pixelParentScreenName: String) {
        this.pixelParentScreenName = pixelParentScreenName
    }

    fun setSharedPrefsKeyForDismiss(sharedPrefsKeyForDismiss: String) {
        this.sharedPrefsKeyForDismiss = sharedPrefsKeyForDismiss
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
            is UpdateNotificationsState -> updateNotificationsState()
            is UpdateNotificationsStateOnAndroid13Plus -> updateNotificationsPermissionsOnAndroid13Plus()
            is OpenSettings -> openSettings()
            is OpenSettingsOnAndroid8Plus -> openSettingsOnAndroid8Plus()
            is DismissComponent -> hideMe()
            is CheckPermissionRationale -> checkPermissionRationale()
            is ShowPermissionRationale -> showNotificationsPermissionsPrompt()
        }
    }

    private fun updateNotificationsState() {
        val enabled = NotificationManagerCompat.from(getActivity()).areNotificationsEnabled()
        viewModel.updateNotificationsPermissions(enabled)
    }

    @SuppressLint("InlinedApi")
    private fun updateNotificationsPermissionsOnAndroid13Plus() {
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        viewModel.updateNotificationsPermissions(granted)
    }

    private fun openSettings() {
        val settingsIntent = Intent(ANDROID_M_APP_NOTIFICATION_SETTINGS)
            .putExtra(ANDROID_M_APP_PACKAGE, context.packageName)
            .putExtra(ANDROID_M_APP_UID, context.applicationInfo.uid)

        startActivity(context, settingsIntent, null)
    }

    @SuppressLint("InlinedApi")
    private fun openSettingsOnAndroid8Plus() {
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

    private fun checkPermissionRationale() {
        @SuppressLint("InlinedApi")
        val showRationale = ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.POST_NOTIFICATIONS)
        viewModel.handleRequestPermissionRationale(showRationale)
    }

    @SuppressLint("InlinedApi")
    private fun showNotificationsPermissionsPrompt() {
        ActivityCompat.requestPermissions(getActivity(), arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
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

    private fun addViewTreeObserverOnGlobalLayoutListener() {
        val tag = R.string.notifyme_button_label
        setTag(tag, visibility)

        vtoGlobalLayoutListener = OnGlobalLayoutListener {
            val newVisibility = visibility
            val previousVisibility = getTag(tag)
            if (newVisibility != previousVisibility) {
                this.visibilityChangedListener?.onVisibilityChange(this, newVisibility == VISIBLE)
            }
        }

        viewTreeObserver.addOnGlobalLayoutListener(vtoGlobalLayoutListener)
    }

    private fun removeViewTreeObserverOnGlobalLayoutListener() {
        if (viewTreeObserver.isAlive && vtoGlobalLayoutListener != null) {
            viewTreeObserver.removeOnGlobalLayoutListener(vtoGlobalLayoutListener)
        }
    }

    companion object {
        private const val ANDROID_M_APP_NOTIFICATION_SETTINGS = "android.settings.APP_NOTIFICATION_SETTINGS"
        private const val ANDROID_M_APP_PACKAGE = "app_package"
        private const val ANDROID_M_APP_UID = "app_uid"
    }

    interface OnVisibilityChangedListener {
        fun onVisibilityChange(v: View?, isVisible: Boolean)
    }
}
