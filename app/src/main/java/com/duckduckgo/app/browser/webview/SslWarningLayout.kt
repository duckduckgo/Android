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

import android.content.Context
import android.util.AttributeSet
import android.webkit.SslErrorHandler
import android.widget.FrameLayout
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.SSLErrorType
import com.duckduckgo.app.browser.SslErrorResponse
import com.duckduckgo.app.browser.databinding.ViewSslWarningBinding
import com.duckduckgo.app.browser.webview.SslWarningLayout.Action.Advance
import com.duckduckgo.app.browser.webview.SslWarningLayout.Action.LeaveSite
import com.duckduckgo.app.browser.webview.SslWarningLayout.Action.Proceed
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.extensions.applyBoldSpanTo
import com.duckduckgo.common.utils.extensions.html

class SslWarningLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    sealed class Action {

        data class Shown(val errorType: SSLErrorType) : Action()
        object Proceed : Action()
        object Advance : Action()
        object LeaveSite : Action()
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

    private fun resetViewState() {
        with(binding) {
            sslErrorAdvancedCTA.show()
            sslErrorAdvancedGroup.gone()
        }
    }

    private fun configureCopy(errorResponse: SslErrorResponse) {
        with(binding) {
            sslErrorHeadline.text = context.getString(R.string.sslErrorHeadline, errorResponse.error.url).applyBoldSpanTo(errorResponse.error.url)
            sslErrorExpandedHeadline.text = context.getString(errorResponse.errorType.errorId, errorResponse.error.url, errorResponse.error.url)
                .applyBoldSpanTo(errorResponse.error.url)
            sslErrorAcceptCta.text = context.getString(R.string.sslErrorExpandedCTA).html(context)
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
            }
        }
    }
}
