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

package com.duckduckgo.app.browser.webview

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.webkit.SslErrorHandler
import android.widget.FrameLayout
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.SSLErrorType
import com.duckduckgo.app.browser.SSLErrorType.WRONG_HOST
import com.duckduckgo.app.browser.SslErrorResponse
import com.duckduckgo.app.browser.databinding.ViewSslWarningBinding
import com.duckduckgo.app.browser.webview.SslWarningLayout.Action.Advance
import com.duckduckgo.app.browser.webview.SslWarningLayout.Action.LeaveSite
import com.duckduckgo.app.browser.webview.SslWarningLayout.Action.Proceed
import com.duckduckgo.common.ui.tabs.SwipingTabsFeatureProvider
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.extensions.applyBoldSpanTo
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.common.utils.extractDomain
import com.duckduckgo.di.scopes.ViewScope
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject

@InjectWith(ViewScope::class)
class SslWarningLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var swipingTabsFeature: SwipingTabsFeatureProvider

    sealed class Action {

        data class Shown(val errorType: SSLErrorType) : Action()
        data object Proceed : Action()
        data object Advance : Action()
        data object LeaveSite : Action()
    }

    private val binding: ViewSslWarningBinding by viewBinding()

    fun bind(
        handler: SslErrorHandler,
        errorResponse: SslErrorResponse,
        actionHandler: (Action) -> Unit,
    ) {
        resetViewState()
        actionHandler.invoke(Action.Shown(errorResponse.errorType))

        with(binding) {
            configureCopy(errorResponse)
            setListeners(handler, actionHandler)
        }
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (swipingTabsFeature.isEnabled) {
            // disable tab swiping on this view
            parent.requestDisallowInterceptTouchEvent(true)
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun resetViewState() {
        with(binding) {
            sslErrorAdvancedCTA.show()
            sslErrorAdvancedGroup.gone()
        }
    }

    private fun configureCopy(errorResponse: SslErrorResponse) {
        with(binding) {
            sslErrorHeadline.text = context.getString(R.string.sslErrorHeadline, errorResponse.error.url).applyBoldSpanTo(errorResponse.error.url)
            sslErrorAcceptCta.text = context.getString(R.string.sslErrorExpandedCTA).html(context)
            formatSecondaryCopy(errorResponse)
        }
    }

    private fun formatSecondaryCopy(errorResponse: SslErrorResponse) {
        with(binding) {
            when (errorResponse.errorType) {
                WRONG_HOST -> {
                    val url = errorResponse.error.url
                    val domain = url.extractDomain()
                    val text = if (domain != null) {
                        val urlDomain = "*.$domain"
                        context.getString(errorResponse.errorType.errorId, url, domain).applyBoldSpanTo(listOf(url, urlDomain))
                    } else {
                        context.getString(errorResponse.errorType.errorId, url, url).applyBoldSpanTo(url)
                    }
                    sslErrorExpandedMessage.text = text
                }
                else -> {
                    sslErrorExpandedMessage.text = context.getString(errorResponse.errorType.errorId, errorResponse.error.url).applyBoldSpanTo(
                        errorResponse.error.url,
                    )
                }
            }
        }
    }

    private fun setListeners(
        handler: SslErrorHandler,
        actionHandler: (Action) -> Unit,
    ) {
        with(binding) {
            sslErrorAcceptCta.setOnClickListener {
                handler.proceed()
                actionHandler.invoke(Proceed)
            }
            sslErrorLeaveSiteCTA.setOnClickListener {
                handler.cancel()
                actionHandler.invoke(LeaveSite)
            }
            sslErrorAdvancedCTA.setOnClickListener {
                actionHandler.invoke(Advance)
                sslErrorAdvancedCTA.gone()
                sslErrorAdvancedGroup.show()
                errorLayout.post {
                    errorLayout.fullScroll(View.FOCUS_DOWN)
                }
            }
        }
    }
}
