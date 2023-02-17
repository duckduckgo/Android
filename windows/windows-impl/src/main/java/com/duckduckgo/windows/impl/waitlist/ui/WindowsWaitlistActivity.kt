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

package com.duckduckgo.windows.impl.waitlist.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.macos_api.MacOsNav
import com.duckduckgo.mobile.android.ui.notifyme.NotifyMeView
import com.duckduckgo.mobile.android.ui.spans.DuckDuckGoClickableSpan
import com.duckduckgo.mobile.android.ui.view.addClickableSpan
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.windows.api.WindowsWaitlistState.InBeta
import com.duckduckgo.windows.api.WindowsWaitlistState.JoinedWaitlist
import com.duckduckgo.windows.api.WindowsWaitlistState.NotJoinedQueue
import com.duckduckgo.windows.impl.R
import com.duckduckgo.windows.impl.databinding.ActivityWindowsWaitlistBinding
import com.duckduckgo.windows.impl.waitlist.ui.WindowsWaitlistViewModel.Command
import com.duckduckgo.windows.impl.waitlist.ui.WindowsWaitlistViewModel.Command.CopyInviteToClipboard
import com.duckduckgo.windows.impl.waitlist.ui.WindowsWaitlistViewModel.Command.GoToMacClientSettings
import com.duckduckgo.windows.impl.waitlist.ui.WindowsWaitlistViewModel.Command.ShareInviteCode
import com.duckduckgo.windows.impl.waitlist.ui.WindowsWaitlistViewModel.Command.ShowErrorMessage
import com.duckduckgo.windows.impl.waitlist.ui.WindowsWaitlistViewModel.ViewState
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

@InjectWith(ActivityScope::class)
class WindowsWaitlistActivity : DuckDuckGoActivity() {

    private val viewModel: WindowsWaitlistViewModel by bindViewModel()
    private val binding: ActivityWindowsWaitlistBinding by viewBinding()

    @Inject lateinit var macOSSettingsNav: MacOsNav

    private val toolbar
        get() = binding.includeToolbar.toolbar

    private val windowsSpan = object : DuckDuckGoClickableSpan() {
        override fun onClick(widget: View) {
            viewModel.onCopyToClipboard(onlyCode = false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.viewState.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).onEach { render(it) }
            .launchIn(lifecycleScope)
        viewModel.commands.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).onEach { executeCommand(it) }
            .launchIn(lifecycleScope)

        setContentView(binding.root)
        setupToolbar(toolbar)
        configureUiEventHandlers()
    }

    private fun configureUiEventHandlers() {
        binding.waitListButton.setOnClickListener {
            it.isEnabled = false
            viewModel.joinTheWaitlist()
        }
        binding.shareImage.setOnClickListener { viewModel.onShareClicked() }
        binding.lookingForMacVersionButton.setOnClickListener {
            viewModel.onGoToMacClicked()
        }
    }

    private fun render(viewState: ViewState) {
        when (val state = viewState.waitlist) {
            is NotJoinedQueue -> renderNotJoinedQueue()
            is JoinedWaitlist -> renderJoinedQueue()
            is InBeta -> renderInBeta(state.inviteCode)
        }
    }

    private fun executeCommand(command: Command) {
        when (command) {
            is ShowErrorMessage -> renderErrorMessage()
            is ShareInviteCode -> launchSharePageChooser(command.inviteCode)
            is CopyInviteToClipboard -> copyToClipboard(command.inviteCode, command.onlyCode)
            is GoToMacClientSettings -> launchMacClientSettings()
        }
    }

    private fun renderInBeta(inviteCode: String) {
        binding.headerImage.setImageResource(com.duckduckgo.mobile.android.R.drawable.ic_donations_large)
        binding.statusTitle.text = getString(R.string.windows_waitlist_code_title)
        binding.waitlistDescription.addClickableSpan(
            getText(R.string.windows_waitlist_code_description),
            listOf(Pair("beta_link", windowsSpan)),
        )
        binding.waitListButton.gone()
        binding.waitlistNotifyMeContainer.gone()
        binding.lookingForMacVersionButton.gone()
        binding.footerDescription.gone()
        binding.codeFrame.show()
        binding.shareImage.show()
        binding.inviteCode.text = inviteCode
        binding.inviteCode.setOnClickListener { viewModel.onCopyToClipboard(onlyCode = true) }
    }

    private fun renderJoinedQueue() {
        binding.waitListButton.gone()
        binding.codeFrame.gone()
        binding.shareImage.gone()
        binding.waitlistNotifyMeContainer.show()
        binding.lookingForMacVersionButton.show()
        binding.footerDescription.gone()
        binding.headerImage.setImageResource(com.duckduckgo.mobile.android.R.drawable.ic_list)
        binding.statusTitle.text = getString(R.string.windows_waitlist_on_the_list_title)
        binding.waitlistDescription.text = getJoinedQueueDescriptionText(binding.waitlistNotifyMe.isVisible)
        binding.waitlistNotifyMe.setOnVisibilityChange(object : NotifyMeView.OnVisibilityChangedListener {
            override fun onVisibilityChange(v: View?, isVisible: Boolean) {
                binding.waitlistDescription.text = getJoinedQueueDescriptionText(isVisible)
            }
        },
        )
    }

    private fun renderNotJoinedQueue() {
        binding.headerImage.setImageResource(com.duckduckgo.mobile.android.R.drawable.ic_computer_win)
        binding.waitListButton.show()
        binding.waitlistNotifyMeContainer.gone()
        binding.lookingForMacVersionButton.show()
        binding.footerDescription.show()
        binding.codeFrame.gone()
        binding.shareImage.gone()
        binding.waitlistDescription.text = getText(R.string.windows_waitlist_description)
    }

    private fun getJoinedQueueDescriptionText(notifyMeVisible: Boolean): String {
        return if (notifyMeVisible) {
            getString(R.string.windows_waitlist_on_the_list_notification_disabled)
        } else {
            getString(R.string.windows_waitlist_on_the_list_notification_enabled)
        }
    }

    private fun renderErrorMessage() {
        binding.waitListButton.isEnabled = true
        Toast.makeText(this, R.string.windows_join_waitlist_error, Toast.LENGTH_LONG).show()
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun launchSharePageChooser(inviteCode: String) {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/html"
            putExtra(Intent.EXTRA_TEXT, getInviteText(inviteCode))
            putExtra(Intent.EXTRA_TITLE, getString(R.string.windows_waitlist_share_invite))
        }

        try {
            startActivity(Intent.createChooser(share, getString(R.string.windows_waitlist_share_invite)))
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "Activity not found")
        }
    }

    private fun copyToClipboard(inviteCode: String, onlyCode: Boolean) {
        val clipboard: ClipboardManager? = ContextCompat.getSystemService(this, ClipboardManager::class.java)
        var text = getInviteText(inviteCode)
        var toastText = getString(R.string.windows_waitlist_clipboard_invite_copied)

        if (onlyCode) {
            text = SpannableString(inviteCode)
            toastText = getString(R.string.windows_waitlist_clipboard_code_copied)
        }

        val clip: ClipData = ClipData.newPlainText(CLIPBOARD_LABEL, text)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(this, toastText, Toast.LENGTH_LONG).show()
    }

    private fun getInviteText(inviteCode: String): Spanned {
        return HtmlCompat.fromHtml(getString(R.string.windows_waitlist_code_share_text, inviteCode), 0)
    }

    private fun launchMacClientSettings() {
        startActivity(macOSSettingsNav.openMacOsSettings(this))
        finish()
    }

    companion object {
        const val CLIPBOARD_LABEL = "INVITE_CODE"

        fun intent(context: Context): Intent {
            return Intent(context, WindowsWaitlistActivity::class.java)
        }
    }
}
