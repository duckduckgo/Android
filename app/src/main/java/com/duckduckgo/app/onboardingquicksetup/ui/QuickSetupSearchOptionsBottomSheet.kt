/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.onboardingquicksetup.ui

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.databinding.BottomSheetQuickSetupSearchOptionsBinding
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.di.scopes.FragmentScope
import com.google.android.material.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import com.duckduckgo.app.browser.R as BrowserR

@InjectWith(FragmentScope::class)
class QuickSetupSearchOptionsBottomSheet : BottomSheetDialogFragment() {

    @Inject
    lateinit var appTheme: AppTheme

    private var initialWithAi: Boolean = true
    private var withAi: Boolean = true

    private var binding: BottomSheetQuickSetupSearchOptionsBinding? = null

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialWithAi = requireArguments().getBoolean(ARG_INITIAL_WITH_AI, true)
        withAi = savedInstanceState?.getBoolean(STATE_WITH_AI) ?: initialWithAi
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding = BottomSheetQuickSetupSearchOptionsBinding.inflate(inflater, container, false)
        this.binding = binding

        with(binding.inputScreenPicker) {
            setLightMode(appTheme.isLightModeEnabled())
            setSelection(this@QuickSetupSearchOptionsBottomSheet.withAi, BrandDesignInputScreenPicker.Transition.ANIMATE)
            setOnSelectionChangedListener { selected ->
                this@QuickSetupSearchOptionsBottomSheet.withAi = selected
                setSelection(selected, BrandDesignInputScreenPicker.Transition.CROSSFADE_ANIMATE)
                setFragmentResult(REQUEST_KEY, bundleOf(RESULT_KEY_WITH_AI to selected))
            }
        }
        binding.quickSetupSearchOptionsCaption.text =
            getString(BrowserR.string.preOnboardingInputScreenDescription).html(requireContext())
        binding.quickSetupSearchOptionsDoneButton.setOnClickListener {
            dismiss()
        }
        binding.quickSetupSearchOptionsCloseButton.setOnClickListener {
            dismiss()
        }
        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.isDraggable = false
        dialog.setOnShowListener(::setRoundCorners)
        return dialog
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_WITH_AI, withAi)
    }

    override fun onDestroyView() {
        binding?.inputScreenPicker?.cancelLottieAnimations()
        binding = null
        super.onDestroyView()
    }

    private fun setRoundCorners(dialogInterface: DialogInterface) {
        val bottomSheet = (dialogInterface as BottomSheetDialog).findViewById<FrameLayout>(R.id.design_bottom_sheet)
        val ctx = requireContext()
        val shapeDrawable = MaterialShapeDrawable.createWithElevationOverlay(ctx)
        shapeDrawable.shapeAppearanceModel = shapeDrawable.shapeAppearanceModel
            .toBuilder()
            .setTopLeftCorner(CornerFamily.ROUNDED, ctx.resources.getDimension(BrowserR.dimen.onboardingBottomSheetCornerRadius))
            .setTopRightCorner(CornerFamily.ROUNDED, ctx.resources.getDimension(BrowserR.dimen.onboardingBottomSheetCornerRadius))
            .build()
        bottomSheet?.background = shapeDrawable
    }

    companion object {
        private const val ARG_INITIAL_WITH_AI = "initialWithAi"
        private const val STATE_WITH_AI = "withAi"

        const val TAG = "QuickSetupSearchOptionsBottomSheetFragment"

        /** Fragment-result request key. The host registers a listener under this key. */
        const val REQUEST_KEY = "QuickSetupSearchOptionsResult"

        /** Bundle key for the chosen `withAi` Boolean (true = Search + Duck.ai, false = Search Only). */
        const val RESULT_KEY_WITH_AI = "withAi"

        fun newInstance(initialWithAi: Boolean): QuickSetupSearchOptionsBottomSheet {
            return QuickSetupSearchOptionsBottomSheet().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_INITIAL_WITH_AI, initialWithAi)
                }
            }
        }
    }
}
