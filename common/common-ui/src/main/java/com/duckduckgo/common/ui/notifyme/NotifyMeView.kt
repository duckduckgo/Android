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

package com.duckduckgo.common.ui.notifyme

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.notifyme.NotifyMeView.Orientation.Center
import com.duckduckgo.common.ui.notifyme.NotifyMeViewModel.Command
import com.duckduckgo.common.ui.notifyme.NotifyMeViewModel.Command.CheckPermissionRationale
import com.duckduckgo.common.ui.notifyme.NotifyMeViewModel.Command.DismissComponent
import com.duckduckgo.common.ui.notifyme.NotifyMeViewModel.Command.OpenSettingsOnAndroid8Plus
import com.duckduckgo.common.ui.notifyme.NotifyMeViewModel.Command.ShowPermissionRationale
import com.duckduckgo.common.ui.notifyme.NotifyMeViewModel.Command.UpdateNotificationsState
import com.duckduckgo.common.ui.notifyme.NotifyMeViewModel.Command.UpdateNotificationsStateOnAndroid13Plus
import com.duckduckgo.common.ui.notifyme.NotifyMeViewModel.ViewState
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.R.styleable
import com.duckduckgo.mobile.android.databinding.ViewNotifyMeViewBinding
import com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
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
    lateinit var viewModelFactory: ViewViewModelFactory

    @Inject
    lateinit var dispatchers: DispatcherProvider

    private lateinit var sharedPrefsKeyForDismiss: String

    private var onNotifyMeButtonClicked: () -> Unit = {}
    private var onNotifyMeCloseButtonClicked: () -> Unit = {}

    private var vtoGlobalLayoutListener: OnGlobalLayoutListener? = null
    private var visibilityChangedListener: OnVisibilityChangedListener? = null

    private val binding: ViewNotifyMeViewBinding by viewBinding()

    private val viewModel: NotifyMeViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[NotifyMeViewModel::class.java]
    }

    private val conflatedStateJob = ConflatedJob()
    private val conflatedCommandJob = ConflatedJob()

    init {
        val attributes = context.obtainStyledAttributes(attrs, R.styleable.NotifyMeView)
        setPrimaryText(attributes.getString(R.styleable.NotifyMeView_primaryText) ?: "")
        setSecondaryText(attributes.getString(R.styleable.NotifyMeView_secondaryText) ?: "")
        setSharedPrefsKeyForDismiss(attributes.getString(R.styleable.NotifyMeView_sharedPrefsKeyForDismiss) ?: "")
        setDismissIcon(attributes.getBoolean(R.styleable.NotifyMeView_dismissIcon, true))
        setContentOrientation(Orientation.from(attributes.getInt(styleable.NotifyMeView_contentOrientation, 0)))
        binding.notifyMeClose.setOnClickListener {
            viewModel.onCloseButtonClicked()
            onNotifyMeCloseButtonClicked.invoke()
        }
        binding.notifyMeButton.setOnClickListener {
            viewModel.onNotifyMeButtonClicked()
            onNotifyMeButtonClicked.invoke()
        }
        attributes.recycle()
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        addViewTreeObserverOnGlobalLayoutListener()

        findViewTreeLifecycleOwner()?.lifecycle?.addObserver(viewModel)

        val coroutineScope = findViewTreeLifecycleOwner()?.lifecycleScope

        viewModel.init(sharedPrefsKeyForDismiss)

        conflatedStateJob += viewModel.viewState
            .onEach { render(it) }
            .launchIn(coroutineScope!!)

        conflatedCommandJob += viewModel.commands()
            .onEach { processCommands(it) }
            .launchIn(coroutineScope!!)

        updateNotificationsState()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        removeViewTreeObserverOnGlobalLayoutListener()

        findViewTreeLifecycleOwner()?.lifecycle?.removeObserver(viewModel)

        conflatedStateJob.cancel()
        conflatedCommandJob.cancel()
    }

    fun setOnVisibilityChange(visibilityChangedListener: OnVisibilityChangedListener) {
        this.visibilityChangedListener = visibilityChangedListener
    }

    fun onDismissClicked(onNotifyMeCloseButtonClicked: () -> Unit) {
        this.onNotifyMeCloseButtonClicked = onNotifyMeCloseButtonClicked
    }

    fun onNotifyMeClicked(onNotifyMeButtonClicked: () -> Unit) {
        this.onNotifyMeButtonClicked = onNotifyMeButtonClicked
    }

    fun setPrimaryText(primaryText: String) {
        binding.notifyMeMessageTitle.text = primaryText
    }

    fun setSecondaryText(secondaryText: String) {
        binding.notifyMeMessageSubtitle.text = secondaryText
    }

    fun setSharedPrefsKeyForDismiss(sharedPrefsKeyForDismiss: String) {
        this.sharedPrefsKeyForDismiss = sharedPrefsKeyForDismiss
    }

    fun setDismissIcon(dismissIcon: Boolean) {
        if (dismissIcon) {
            binding.notifyMeClose.show()
        } else {
            binding.notifyMeClose.gone()
        }
    }

    fun setContentOrientation(contentOrientation: Orientation) {
        if (contentOrientation == Center) {
            binding.notifyMeButton.iconGravity = ICON_GRAVITY_TEXT_START
            binding.notifyMeButton.updateLayoutParams {
                width = ConstraintLayout.LayoutParams.MATCH_PARENT
            }

            binding.notifyMeMessageTitle.gravity = Gravity.CENTER
            binding.notifyMeMessageSubtitle.gravity = Gravity.CENTER
            binding.notifyMeContent.updateLayoutParams<LayoutParams> {
                marginEnd = context.resources.getDimensionPixelSize(R.dimen.keyline_2)
            }
        }
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

    @SuppressLint("InlinedApi")
    private fun openSettingsOnAndroid8Plus() {
        val settingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

        startActivity(context, settingsIntent, null)
    }

    private fun showMe() {
        this.show()
        binding.notifyMeCard.show()
    }

    private fun hideMe() {
        this.gone()
        binding.notifyMeCard.gone()
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
                setTag(tag, newVisibility)
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

    enum class Orientation {
        Start,
        Center,
        ;

        companion object {
            fun from(orientation: Int): Orientation {
                // same order as attrs-notify-me-view.xml
                return when (orientation) {
                    0 -> Start
                    1 -> Center
                    else -> Start
                }
            }
        }
    }
}
