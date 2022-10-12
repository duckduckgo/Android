/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.ui.alwayson

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.ui.store.AppTheme
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.databinding.ContentVpnAlwaysOnAlertBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.android.support.AndroidSupportInjection
import java.lang.ref.WeakReference
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class AlwaysOnAlertDialogFragment private constructor() : BottomSheetDialogFragment() {

    @Inject lateinit var appBuildConfig: AppBuildConfig
    @Inject lateinit var appTheme: AppTheme
    private var listener: WeakReference<Listener> = WeakReference(null)

    override fun getTheme(): Int = R.style.AlwaysOnBottomSheetDialogTheme

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ContentVpnAlwaysOnAlertBinding.inflate(inflater, container, false).apply {
            configureViews(this)
        }.root
    }

    private fun configureViews(binding: ContentVpnAlwaysOnAlertBinding) {
        fun animatedClosed() {
            (dialog as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_HIDDEN
        }

        fun configureCloseButtons(binding: ContentVpnAlwaysOnAlertBinding) {
            binding.closeButton.setOnClickListener { animatedClosed() }
            binding.notNowButton.setOnClickListener { animatedClosed() }
            binding.goToSettingsButton.setOnClickListener {
                animatedClosed()
                listener.get()?.onGoToSettingsClicked()
            }
        }

        fun configurePromotionIllustration(binding: ContentVpnAlwaysOnAlertBinding) {
            binding.alwaysOnIllustration.setAnimation(
                if (appTheme.isLightModeEnabled()) {
                    R.raw.always_on
                } else {
                    R.raw.always_on_dark
                }
            )
        }

        fun configureBehavior() {
            (dialog as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        fun bindViewElements() {
            binding.alwaysOnModalDescription.text = HtmlCompat.fromHtml(getString(R.string.atp_AlwaysOnModalBody), 0)
        }

        configureBehavior()
        configureCloseButtons(binding)
        configurePromotionIllustration(binding)
        bindViewElements()
    }

    fun interface Listener {
        /**
         * Called when the user clicks on the "Go to settings" button.
         */
        fun onGoToSettingsClicked()
    }

    companion object {
        fun newInstance(listener: Listener? = null): AlwaysOnAlertDialogFragment {
            return AlwaysOnAlertDialogFragment().apply {
                this.listener = WeakReference(listener)
            }
        }
    }
}
