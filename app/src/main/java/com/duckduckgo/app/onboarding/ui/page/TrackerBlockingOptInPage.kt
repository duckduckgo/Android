/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.onboarding.ui.page

import android.content.Context
import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProviders
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.ViewModelFactory
import com.duckduckgo.app.onboarding.ui.page.dialog.TrackerBlockingOptOutConfirmationDialog
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName
import dagger.android.support.AndroidSupportInjection
import kotlinx.android.synthetic.main.content_onboarding_tracker_blocking_opt_in.*
import javax.inject.Inject


class TrackerBlockerOptInPage : OnboardingPageFragment(), TrackerBlockingOptOutConfirmationDialog.OptOutConfirmationDialogListener {

    interface TrackerBlockingDecisionListener {
        fun onUserEnabledTrackerBlocking()
        fun onUserDisabledTrackerBlocking()
    }

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    @Inject
    lateinit var pixel: Pixel

    private val viewModel by bindViewModel<TrackerBlockingSelectionViewModel>()

    override fun layoutResource(): Int = R.layout.content_onboarding_tracker_blocking_opt_in

    private val activityListener: TrackerBlockingDecisionListener?
        get() = activity as TrackerBlockingDecisionListener

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        acceptTrackerBlocking.setOnClickListener { onTrackerBlockingEnabledPress() }
        declineTrackerBlocking.setOnClickListener { onTrackerBlockingDisabledPress() }
    }

    private fun onTrackerBlockingEnabledPress() {
        viewModel.onTrackerBlockingEnabled()
        activityListener?.onUserEnabledTrackerBlocking()

        pixel.fire(PixelName.ONBOARDING_TRACKER_BLOCKING_USER_OPTED_IN)
    }

    private fun onTrackerBlockingDisabledPress() {
        showTrackerBlockingDisabledConfirmation()
        pixel.fire(PixelName.ONBOARDING_TRACKER_BLOCKING_USER_DECLINED)
    }

    override fun userCancelledOptOutConfirmationDialog() {
        pixel.fire(PixelName.ONBOARDING_TRACKER_BLOCKING_OPT_OUT_DIALOG_CANCELLED)
    }

    override fun continueWithoutTrackerBlocking() {
        viewModel.onTrackerBlockingDisabled()
        activityListener?.onUserDisabledTrackerBlocking()

        pixel.fire(PixelName.ONBOARDING_TRACKER_BLOCKING_OPT_OUT_DIALOG_CONTINUE_ANYWAY)
    }

    private fun showTrackerBlockingDisabledConfirmation() {
        fragmentManager?.let {
            val fragment = TrackerBlockingOptOutConfirmationDialog()
            fragment.listener = this
            fragment.show(it, BLOCKING_DISABLED_CONFIRMATION_FRAGMENT_TAG)
        }
    }

    private inline fun <reified V : ViewModel> bindViewModel() = lazy { ViewModelProviders.of(this, viewModelFactory).get(V::class.java) }

    companion object {
        private const val BLOCKING_DISABLED_CONFIRMATION_FRAGMENT_TAG = "disabled"
    }
}
