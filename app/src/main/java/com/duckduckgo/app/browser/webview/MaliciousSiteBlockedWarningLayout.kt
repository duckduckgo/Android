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
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.core.text.HtmlCompat
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ViewMaliciousSiteBlockedWarningBinding
import com.duckduckgo.app.browser.webview.MaliciousSiteBlockedWarningLayout.Action.LearnMore
import com.duckduckgo.app.browser.webview.MaliciousSiteBlockedWarningLayout.Action.LeaveSite
import com.duckduckgo.app.browser.webview.MaliciousSiteBlockedWarningLayout.Action.ReportError
import com.duckduckgo.app.browser.webview.MaliciousSiteBlockedWarningLayout.Action.VisitSite
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed.MALWARE
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed.PHISHING

class MaliciousSiteBlockedWarningLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    sealed class Action {
        data object VisitSite : Action()
        data object LeaveSite : Action()
        data object LearnMore : Action()
        data object ReportError : Action()
    }

    private val binding: ViewMaliciousSiteBlockedWarningBinding by viewBinding()

    fun bind(
        feed: Feed,
        actionHandler: (Action) -> Unit,
    ) {
        resetViewState()

        formatCopy(feed, actionHandler)
        setListeners(actionHandler)
    }

    private fun resetViewState() {
        with(binding) {
            advancedCTA.show()
            advancedGroup.gone()
        }
    }

    private fun formatCopy(
        feed: Feed,
        actionHandler: (Action) -> Unit,
    ) {
        with(binding) {
            val errorResource = when (feed) {
                MALWARE -> {
                    R.string.maliciousSiteMalwareHeadline
                }
                PHISHING -> {
                    R.string.maliciousSitePhishingHeadline
                }
            }
            errorHeadline.setSpannable(errorResource) { actionHandler(LearnMore) }
            expandedHeadline.setSpannable(R.string.maliciousSiteExpandedHeadline) { actionHandler(ReportError) }
            expandedCTA.text = HtmlCompat.fromHtml(context.getString(R.string.maliciousSiteExpandedCTA), HtmlCompat.FROM_HTML_MODE_LEGACY)
        }
    }

    private fun DaxTextView.setSpannable(
        @StringRes errorResource: Int,
        actionHandler: () -> Unit,
    ) {
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                actionHandler()
            }
        }
        val htmlContent = context.getString(errorResource).html(context)
        val spannableString = SpannableStringBuilder(htmlContent)
        val urlSpans = htmlContent.getSpans(0, htmlContent.length, URLSpan::class.java)
        urlSpans?.forEach {
            spannableString.apply {
                setSpan(
                    clickableSpan,
                    spannableString.getSpanStart(it),
                    spannableString.getSpanEnd(it),
                    spannableString.getSpanFlags(it),
                )
                removeSpan(it)
                trim()
            }
        }
        text = spannableString
        movementMethod = LinkMovementMethod.getInstance()
    }

    private fun setListeners(
        actionHandler: (Action) -> Unit,
    ) {
        with(binding) {
            leaveSiteCTA.setOnClickListener {
                actionHandler(LeaveSite)
            }
            advancedCTA.setOnClickListener {
                advancedCTA.gone()
                advancedGroup.show()
                maliciousSiteLayout.post {
                    maliciousSiteLayout.fullScroll(View.FOCUS_DOWN)
                }
            }
            expandedCTA.setOnClickListener {
                actionHandler(VisitSite)
            }
        }
    }
}
