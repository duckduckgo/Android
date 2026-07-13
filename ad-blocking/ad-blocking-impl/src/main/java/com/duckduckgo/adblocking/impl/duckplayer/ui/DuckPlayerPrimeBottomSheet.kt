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

package com.duckduckgo.adblocking.impl.duckplayer.ui

import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import com.duckduckgo.adblocking.impl.R
import com.duckduckgo.adblocking.impl.databinding.ModalDuckPlayerBinding
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.applyBottomSystemBarInsetPadding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeBucket
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class DuckPlayerPrimeBottomSheet : BottomSheetDialogFragment() {

    private lateinit var binding: ModalDuckPlayerBinding
    private val isFromDuckPlayerPage: Boolean by lazy { requireArguments().getBoolean(FROM_DUCK_PLAYER_PAGE) }

    @Inject
    lateinit var edgeToEdgeProvider: EdgeToEdgeProvider

    override fun getTheme(): Int = if (edgeToEdgeProvider.isEnabled(EdgeToEdgeBucket.BOTTOM_SHEETS)) {
        R.style.DuckPlayerBottomSheetDialogThemeEdgeToEdge
    } else {
        R.style.DuckPlayerBottomSheetDialogTheme
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = ModalDuckPlayerBinding.inflate(inflater, container, false)
        LottieCompositionFactory.fromRawRes(context, R.raw.duckplayer)
        binding.duckPlayerAnimation.setAnimation(R.raw.duckplayer)
        binding.duckPlayerAnimation.playAnimation()
        binding.duckPlayerAnimation.repeatCount = LottieDrawable.INFINITE
        binding.title.text =
            if (isFromDuckPlayerPage) {
                getString(R.string.duck_player_info_modal_title_from_duck_player_page)
            } else {
                getString(R.string.duck_player_info_modal_title_from_overlay)
            }
        binding.dismissButton.setOnClickListener {
            dismiss()
        }
        binding.closeButton.setOnClickListener {
            dismiss()
        }
        if (edgeToEdgeProvider.isEnabled(EdgeToEdgeBucket.BOTTOM_SHEETS)) {
            binding.root.applyBottomSystemBarInsetPadding()
        }
        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        return dialog
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        dismiss()
    }

    companion object {
        fun newInstance(fromDuckPlayerPage: Boolean): DuckPlayerPrimeBottomSheet =
            DuckPlayerPrimeBottomSheet().also {
                it.arguments = Bundle().apply {
                    putBoolean(FROM_DUCK_PLAYER_PAGE, fromDuckPlayerPage)
                }
            }
    }
}
