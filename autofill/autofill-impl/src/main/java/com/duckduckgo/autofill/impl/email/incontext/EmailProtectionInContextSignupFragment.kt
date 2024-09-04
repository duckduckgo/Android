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

package com.duckduckgo.autofill.impl.email.incontext

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebSettings
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.webkit.WebViewCompat
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.AutofillWebMessageRequest
import com.duckduckgo.autofill.api.BrowserAutofill
import com.duckduckgo.autofill.api.Callback
import com.duckduckgo.autofill.api.EmailProtectionInContextSignUpHandleVerificationLink
import com.duckduckgo.autofill.api.EmailProtectionInContextSignUpScreenResult
import com.duckduckgo.autofill.api.EmailProtectionInContextSignUpStartScreen
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.domain.app.LoginTriggerType
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.autofill.impl.InternalAutofillCapabilityChecker
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.configuration.integration.modern.listener.email.incontext.WebMessageListenerCloseEmailProtectionTab
import com.duckduckgo.autofill.impl.databinding.FragmentEmailProtectionInContextSignupBinding
import com.duckduckgo.autofill.impl.email.incontext.EmailProtectionInContextSignupViewModel.ExitButtonAction
import com.duckduckgo.autofill.impl.email.incontext.EmailProtectionInContextSignupViewModel.ViewState
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.navigation.api.getActivityParams
import com.duckduckgo.user.agent.api.UserAgentProvider
import javax.inject.Inject
import kotlinx.coroutines.launch

@InjectWith(FragmentScope::class)
class EmailProtectionInContextSignupFragment :
    DuckDuckGoFragment(R.layout.fragment_email_protection_in_context_signup),
    EmailProtectionInContextSignUpWebChromeClient.ProgressListener,
    WebMessageListenerCloseEmailProtectionTab.CloseEmailProtectionTabCallback {

    @Inject
    lateinit var userAgentProvider: UserAgentProvider

    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var browserAutofill: BrowserAutofill

    @Inject
    lateinit var emailManager: EmailManager

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var autofillCapabilityChecker: InternalAutofillCapabilityChecker

    @Inject
    lateinit var webMessageListener: WebMessageListenerCloseEmailProtectionTab

    val viewModel by lazy {
        ViewModelProvider(requireActivity(), viewModelFactory)[EmailProtectionInContextSignupViewModel::class.java]
    }

    private val autofillConfigurationJob = ConflatedJob()

    private val binding: FragmentEmailProtectionInContextSignupBinding by viewBinding()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        initialiseToolbar()
        activity?.setTitle(R.string.autofillEmailProtectionInContextSignUpDialogFeatureName)
        configureWebView()
        configureBackButtonHandler()
        observeViewState()
        configureEmailManagerObserver()
        loadFirstWebpage(activity?.intent)
    }

    private fun loadFirstWebpage(intent: Intent?) {
        lifecycleScope.launch(dispatchers.main()) {
            autofillConfigurationJob.join()

            val url = intent?.getActivityParams(EmailProtectionInContextSignUpHandleVerificationLink::class.java)?.url ?: STARTING_URL
            binding.webView.loadUrl(url)

            if (url == STARTING_URL) {
                viewModel.loadedStartingUrl()
            }
        }
    }

    private fun configureEmailManagerObserver() {
        lifecycleScope.launch(dispatchers.main()) {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                emailManager.signedInFlow().collect { signedIn ->
                    viewModel.signedInStateUpdated(signedIn, binding.webView.url)
                }
            }
        }
    }

    private fun observeViewState() {
        lifecycleScope.launch(dispatchers.main()) {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.viewState.collect { viewState ->
                    when (viewState) {
                        is ViewState.CancellingInContextSignUp -> cancelInContextSignUp()
                        is ViewState.ConfirmingCancellationOfInContextSignUp -> confirmCancellationOfInContextSignUp()
                        is ViewState.NavigatingBack -> navigateWebViewBack()
                        is ViewState.ShowingWebContent -> showWebContent(viewState)
                        is ViewState.ExitingAsSuccess -> closeActivityAsSuccessfulSignup()
                    }
                }
            }
        }
    }

    private fun showWebContent(viewState: ViewState.ShowingWebContent) {
        when (viewState.urlActions.exitButton) {
            ExitButtonAction.Disabled -> getToolbar().navigationIcon = null
            ExitButtonAction.ExitWithConfirmation -> {
                getToolbar().run {
                    setNavigationIconAsCross()
                    setNavigationOnClickListener { confirmCancellationOfInContextSignUp() }
                }
            }

            ExitButtonAction.ExitWithoutConfirmation -> {
                getToolbar().run {
                    setNavigationIconAsCross()
                    setNavigationOnClickListener {
                        viewModel.userCancelledSignupWithoutConfirmation()
                    }
                }
            }

            ExitButtonAction.ExitTreatAsSuccess -> {
                getToolbar().run {
                    setNavigationIconAsCross()
                    setNavigationOnClickListener {
                        lifecycleScope.launch(dispatchers.io()) {
                            closeActivityAsSuccessfulSignup()
                        }
                    }
                }
            }
        }
    }

    private suspend fun cancelInContextSignUp() {
        activity?.let {
            val intent = viewModel.buildResponseIntent(getMessageRequestId())
            it.setResult(EmailProtectionInContextSignUpScreenResult.CANCELLED, intent)
            it.finish()
        }
    }

    private suspend fun closeActivityAsSuccessfulSignup() {
        activity?.let {
            val intent = viewModel.buildResponseIntent(getMessageRequestId())
            it.setResult(EmailProtectionInContextSignUpScreenResult.SUCCESS, intent)
            it.finish()
        }
    }

    private fun navigateWebViewBack() {
        val previousUrl = getPreviousWebPageUrl()
        binding.webView.goBack()
        viewModel.consumedBackNavigation(previousUrl)
    }

    private fun confirmCancellationOfInContextSignUp() {
        context?.let {
            TextAlertDialogBuilder(it)
                .setTitle(R.string.autofillEmailProtectionInContextSignUpConfirmExitDialogTitle)
                .setPositiveButton(R.string.autofillEmailProtectionInContextSignUpConfirmExitDialogPositiveButton)
                .setNegativeButton(R.string.autofillEmailProtectionInContextSignUpConfirmExitDialogNegativeButton)
                .addEventListener(
                    object : TextAlertDialogBuilder.EventListener() {
                        override fun onPositiveButtonClicked() {
                            viewModel.onUserDecidedNotToCancelInContextSignUp()
                        }

                        override fun onNegativeButtonClicked() {
                            viewModel.onUserConfirmedCancellationOfInContextSignUp()
                        }
                    },
                )
                .show()
        }
    }

    private fun configureBackButtonHandler() {
        activity?.let {
            it.onBackPressedDispatcher.addCallback(
                it,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        viewModel.onBackButtonPressed(url = binding.webView.url, canGoBack = binding.webView.canGoBack())
                    }
                },
            )
        }
    }

    private fun initialiseToolbar() {
        with(getToolbar()) {
            title = getString(R.string.autofillEmailProtectionInContextSignUpDialogFeatureName)
            setNavigationIconAsCross()
            setNavigationOnClickListener { activity?.onBackPressed() }
        }
    }

    private fun Toolbar.setNavigationIconAsCross() {
        setNavigationIcon(com.duckduckgo.mobile.android.R.drawable.ic_close_24)
    }

    private fun getMessageRequestId(): String {
        val intent = activity?.intent
        return intent?.getActivityParams(EmailProtectionInContextSignUpStartScreen::class.java)?.messageRequestId ?: intent?.getActivityParams(
            EmailProtectionInContextSignUpHandleVerificationLink::class.java,
        )?.messageRequestId!!
    }

    @SuppressLint("SetJavaScriptEnabled", "RequiresFeature")
    private fun configureWebView() {
        binding.webView.let {
            it.webChromeClient = EmailProtectionInContextSignUpWebChromeClient(this)

            it.settings.apply {
                userAgentString = userAgentProvider.userAgent()
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                setSupportMultipleWindows(true)
                databaseEnabled = false
                setSupportZoom(true)
            }

            autofillConfigurationJob += lifecycleScope.launch(dispatchers.main()) {
                if (!autofillCapabilityChecker.webViewSupportsAutofill()) {
                    activity?.finish()
                    return@launch
                }

                webMessageListener.callback = this@EmailProtectionInContextSignupFragment
                WebViewCompat.addWebMessageListener(it, webMessageListener.key, webMessageListener.origins, webMessageListener)

                browserAutofill.addJsInterface(
                    webView = it,
                    tabId = "",
                    autofillCallback = noOpCallback,
                )
            }
        }
    }

    companion object {
        private const val STARTING_URL = "https://duckduckgo.com/email/start-incontext"
    }

    override fun onPageFinished(url: String) {
        viewModel.onPageFinished(url)
    }

    private fun getPreviousWebPageUrl(): String? {
        val webHistory = binding.webView.copyBackForwardList()
        val currentIndex = webHistory.currentIndex
        if (currentIndex < 0) return null
        val previousIndex = currentIndex - 1
        if (previousIndex < 0) return null
        return webHistory.getItemAtIndex(previousIndex)?.url
    }

    private fun getToolbar() = (activity as EmailProtectionInContextSignupActivity).binding.includeToolbar.toolbar

    override suspend fun closeNativeInContextEmailProtectionSignup() {
        closeActivityAsSuccessfulSignup()
    }

    private val noOpCallback = object : Callback {
        override suspend fun onCredentialsAvailableToInject(
            autofillWebMessageRequest: AutofillWebMessageRequest,
            credentials: List<LoginCredentials>,
            triggerType: LoginTriggerType,
        ) {
        }

        override suspend fun onCredentialsAvailableToSave(
            autofillWebMessageRequest: AutofillWebMessageRequest,
            credentials: LoginCredentials,
        ) {
        }

        override suspend fun onGeneratedPasswordAvailableToUse(
            autofillWebMessageRequest: AutofillWebMessageRequest,
            username: String?,
            generatedPassword: String,
        ) {
        }

        override fun showNativeChooseEmailAddressPrompt(autofillWebMessageRequest: AutofillWebMessageRequest) {
        }

        override fun showNativeInContextEmailProtectionSignupPrompt(autofillWebMessageRequest: AutofillWebMessageRequest) {
        }

        override fun onCredentialsSaved(savedCredentials: LoginCredentials) {
        }
    }
}
