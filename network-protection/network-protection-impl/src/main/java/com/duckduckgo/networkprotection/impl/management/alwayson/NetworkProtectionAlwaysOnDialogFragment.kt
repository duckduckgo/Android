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

package com.duckduckgo.networkprotection.impl.management.alwayson

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.utils.extensions.getSerializable
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.networkprotection.impl.R
import com.duckduckgo.networkprotection.impl.databinding.DialogNetpAlwaysOnBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.android.support.AndroidSupportInjection
import logcat.logcat
import javax.inject.Inject

private enum class FragmentType {
    PROMOTION,
    LOCKDOWN,
}

@InjectWith(FragmentScope::class)
class NetworkProtectionAlwaysOnDialogFragment : BottomSheetDialogFragment() {

    @Inject lateinit var appTheme: AppTheme

    private lateinit var fragmentType: FragmentType
    private lateinit var listener: Listener

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
        return DialogNetpAlwaysOnBinding.inflate(inflater, container, false).apply {
            fragmentType = requireArguments().getSerializable<FragmentType>(ARGUMENT_FRAGMENT_TYPE) ?: FragmentType.PROMOTION
            configureViews(this)
        }.root
    }

    private fun configureViews(binding: DialogNetpAlwaysOnBinding) {
        fun animatedClosed() {
            (dialog as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_HIDDEN
        }

        fun configureCloseButtons(binding: DialogNetpAlwaysOnBinding) {
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

        fun configurePromotionIllustration(binding: DialogNetpAlwaysOnBinding) {
            binding.alwaysOnIllustration.setAnimation(
                if (appTheme.isLightModeEnabled()) {
                    if (fragmentType == FragmentType.PROMOTION) {
                        R.raw.always_on
                    } else {
                        R.raw.always_on_lockdown
                    }
                } else {
                    if (fragmentType == FragmentType.PROMOTION) {
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
            binding.apply {
                alwaysOnModalHeading.text = getAlwaysOnStringForType(R.string.netpAlwaysOnPromotionHeading, R.string.netpAlwaysOnLockdownHeading)
                alwaysOnModalDescription.text = getAlwaysOnStringForType(R.string.netpAlwaysOnPromotionBody, R.string.netpAlwaysOnLockdownBody)
                goToSettingsButton.text = getString(R.string.netpActionGoToSettings)
                notNowButton.text = getString(R.string.netpActionNotNow)
            }
        }

        configureBehavior()
        configureCloseButtons(binding)
        configurePromotionIllustration(binding)
        bindViewElements()
    }

    private fun getAlwaysOnStringForType(
        @StringRes promotionString: Int,
        @StringRes lockdownString: Int,
    ): CharSequence = if (fragmentType == FragmentType.PROMOTION) {
        promotionString
    } else {
        lockdownString
    }.run {
        getText(this)
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
        fun newPromotionDialog(listener: Listener): NetworkProtectionAlwaysOnDialogFragment {
            return NetworkProtectionAlwaysOnDialogFragment().apply {
                this.listener = listener
                arguments = Bundle().also {
                    it.putSerializable(ARGUMENT_FRAGMENT_TYPE, FragmentType.PROMOTION)
                }
            }
        }

        fun newLockdownDialog(listener: Listener): NetworkProtectionAlwaysOnDialogFragment {
            return NetworkProtectionAlwaysOnDialogFragment().apply {
                this.listener = listener
                arguments = Bundle().also {
                    it.putSerializable(ARGUMENT_FRAGMENT_TYPE, FragmentType.LOCKDOWN)
                }
            }
        }
    }
}
