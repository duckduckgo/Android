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
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.utils.extensions.getSerializable
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.databinding.ContentVpnAlwaysOnAlertBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.android.support.AndroidSupportInjection
import logcat.logcat
import javax.inject.Inject

private enum class FragmentType {
    ALWAYS_ON,
    ALWAYS_ON_LOCKDOWN,
}

@InjectWith(FragmentScope::class)
class AlwaysOnAlertDialogFragment : BottomSheetDialogFragment() {

    @Inject lateinit var appBuildConfig: AppBuildConfig

    @Inject lateinit var appTheme: AppTheme
    private lateinit var listener: Listener
    private lateinit var fragmentType: FragmentType

    override fun getTheme(): Int = com.duckduckgo.mobile.android.R.style.Widget_DuckDuckGo_BottomSheetDialogCollapsed

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ContentVpnAlwaysOnAlertBinding.inflate(inflater, container, false).apply {
            fragmentType = requireArguments().getSerializable<FragmentType>(ARGUMENT_FRAGMENT_TYPE) ?: FragmentType.ALWAYS_ON
            configureViews(this)
        }.root
    }

    private fun configureViews(binding: ContentVpnAlwaysOnAlertBinding) {
        fun animatedClosed() {
            (dialog as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_HIDDEN
        }

        fun configureCloseButtons(binding: ContentVpnAlwaysOnAlertBinding) {
            binding.closeButton.setOnClickListener {
                if (this::listener.isInitialized) {
                    listener.onCanceled()
                } else {
                    logcat { "Listener not initialized" }
                }
                animatedClosed()
            }
            binding.notNowButton.setOnClickListener {
                animatedClosed()
                if (this::listener.isInitialized) {
                    listener.onCanceled()
                } else {
                    logcat { "Listener not initialized" }
                }
            }
            binding.goToSettingsButton.setOnClickListener {
                animatedClosed()
                if (this::listener.isInitialized) {
                    listener.onGoToSettingsClicked()
                } else {
                    logcat { "Listener not initialized" }
                }
            }
        }

        fun configurePromotionIllustration(binding: ContentVpnAlwaysOnAlertBinding) {
            binding.alwaysOnIllustration.setAnimation(
                if (appTheme.isLightModeEnabled()) {
                    if (fragmentType == FragmentType.ALWAYS_ON) {
                        R.raw.always_on
                    } else {
                        R.raw.always_on_lockdown
                    }
                } else {
                    if (fragmentType == FragmentType.ALWAYS_ON) {
                        R.raw.always_on_dark
                    } else {
                        R.raw.always_on_lockdown_dark
                    }
                },
            )
        }

        fun configureBehavior() {
            (dialog as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        fun bindViewElements() {
            if (fragmentType == FragmentType.ALWAYS_ON) {
                binding.alwaysOnModalHeading.text = HtmlCompat.fromHtml(getString(R.string.atp_AlwaysOnModalHeading), 0)
                binding.alwaysOnModalDescription.text = HtmlCompat.fromHtml(getString(R.string.atp_AlwaysOnModalBody), 0)
            } else {
                binding.alwaysOnModalHeading.text = HtmlCompat.fromHtml(getString(R.string.atp_AlwaysOnLockdownModalHeading), 0)
                binding.alwaysOnModalDescription.text = HtmlCompat.fromHtml(getString(R.string.atp_AlwaysOnLockdownModalBody), 0)
            }
        }

        configureBehavior()
        configureCloseButtons(binding)
        configurePromotionIllustration(binding)
        bindViewElements()
    }

    interface Listener {
        /**
         * Called when the user clicks on the "Go to settings" button.
         */
        fun onGoToSettingsClicked()

        /** Called when the user clicks on the "Not now" button. */
        fun onCanceled()
    }

    companion object {
        private const val ARGUMENT_FRAGMENT_TYPE = "fragmentType"
        fun newAlwaysOnDialog(listener: Listener): AlwaysOnAlertDialogFragment {
            return AlwaysOnAlertDialogFragment().apply {
                this.listener = listener
                arguments = Bundle().also {
                    it.putSerializable(ARGUMENT_FRAGMENT_TYPE, FragmentType.ALWAYS_ON)
                }
            }
        }

        fun newAlwaysOnLockdownDialog(listener: Listener): AlwaysOnAlertDialogFragment {
            return AlwaysOnAlertDialogFragment().apply {
                this.listener = listener
                arguments = Bundle().also {
                    it.putSerializable(ARGUMENT_FRAGMENT_TYPE, FragmentType.ALWAYS_ON_LOCKDOWN)
                }
            }
        }
    }
}
