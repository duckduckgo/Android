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

package com.duckduckgo.autofill.impl.email.incontext

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebSettings
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.BrowserAutofill
import com.duckduckgo.autofill.api.EmailProtectionInContextSignUpHandleVerificationLink
import com.duckduckgo.autofill.api.EmailProtectionInContextSignUpScreenNoParams
import com.duckduckgo.autofill.api.EmailProtectionInContextSignUpScreenResult
import com.duckduckgo.autofill.api.EmailProtectionInContextSignupFlowListener
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.autofill.api.emailprotection.EmailInjector
import com.duckduckgo.autofill.impl.AutofillJavascriptInterface
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.ActivityEmailProtectionInContextSignupBinding
import com.duckduckgo.autofill.impl.email.incontext.EmailProtectionInContextSignupViewModel.ExitButtonAction
import com.duckduckgo.autofill.impl.email.incontext.EmailProtectionInContextSignupViewModel.ViewState
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.getActivityParams
import com.duckduckgo.user.agent.api.UserAgentProvider
import kotlinx.coroutines.launch
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(EmailProtectionInContextSignUpScreenNoParams::class)
@ContributeToActivityStarter(EmailProtectionInContextSignUpHandleVerificationLink::class)
class EmailProtectionInContextSignupActivity :
    DuckDuckGoActivity(),
    EmailProtectionInContextSignUpWebChromeClient.ProgressListener,
    EmailProtectionInContextSignUpWebViewClient.NewPageCallback {

    val binding: ActivityEmailProtectionInContextSignupBinding by viewBinding()
    private val viewModel: EmailProtectionInContextSignupViewModel by bindViewModel()

    @Inject
    lateinit var userAgentProvider: UserAgentProvider

    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var emailInjector: EmailInjector

    @Inject
    lateinit var configurator: BrowserAutofill.Configurator

    @Inject
    lateinit var autofillInterface: AutofillJavascriptInterface

    @Inject
    lateinit var emailManager: EmailManager

    @Inject
    lateinit var pixel: Pixel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        initialiseToolbar()
        setTitle(R.string.autofillEmailProtectionInContextSignUpDialogFeatureName)
        configureWebView()
        configureBackButtonHandler()
        observeViewState()
        configureEmailManagerObserver()
        loadFirstWebpage(intent)
    }

    private fun loadFirstWebpage(intent: Intent?) {
        val url = intent?.getActivityParams(EmailProtectionInContextSignUpHandleVerificationLink::class.java)?.url ?: STARTING_URL
        binding.webView.loadUrl(url)

        if (url == STARTING_URL) {
            viewModel.loadedStartingUrl()
        }
    }

    private fun configureEmailManagerObserver() {
        lifecycleScope.launch(dispatchers.main()) {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                emailManager.signedInFlow().collect() { signedIn ->
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
                    setNavigationOnClickListener { closeActivityAsSuccessfulSignup() }
                }
            }
        }
    }

    private fun cancelInContextSignUp() {
        setResult(EmailProtectionInContextSignUpScreenResult.CANCELLED)
        finish()
    }

    private fun closeActivityAsSuccessfulSignup() {
        setResult(EmailProtectionInContextSignUpScreenResult.SUCCESS)
        finish()
    }

    private fun navigateWebViewBack() {
        val previousUrl = getPreviousWebPageUrl()
        binding.webView.goBack()
        viewModel.consumedBackNavigation(previousUrl)
    }

    private fun confirmCancellationOfInContextSignUp() {
        TextAlertDialogBuilder(this)
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

    private fun configureBackButtonHandler() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    viewModel.onBackButtonPressed(url = binding.webView.url, canGoBack = binding.webView.canGoBack())
                }
            },
        )
    }

    private fun initialiseToolbar() {
        with(getToolbar()) {
            title = getString(R.string.autofillEmailProtectionInContextSignUpDialogFeatureName)
            setNavigationIconAsCross()
            setNavigationOnClickListener { onBackPressed() }
        }
    }

    private fun Toolbar.setNavigationIconAsCross() {
        setNavigationIcon(com.duckduckgo.mobile.android.R.drawable.ic_close_24)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        binding.webView.let {
            it.webViewClient = EmailProtectionInContextSignUpWebViewClient(this)
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

            it.addJavascriptInterface(autofillInterface, AutofillJavascriptInterface.INTERFACE_NAME)
            autofillInterface.webView = it
            autofillInterface.emailProtectionInContextSignupFlowCallback = object : EmailProtectionInContextSignupFlowListener {
                override fun closeInContextSignup() {
                    closeActivityAsSuccessfulSignup()
                }
            }

            emailInjector.addJsInterface(it, {}, {})
        }
    }

    companion object {
        private const val STARTING_URL = "https://duckduckgo.com/email/start-incontext"

        fun intent(context: Context): Intent {
            return Intent(context, EmailProtectionInContextSignupActivity::class.java)
        }
    }

    override fun onPageStarted(url: String) {
        configurator.configureAutofillForCurrentPage(binding.webView, url)
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

    private fun getToolbar() = binding.includeToolbar.toolbar as Toolbar
}
