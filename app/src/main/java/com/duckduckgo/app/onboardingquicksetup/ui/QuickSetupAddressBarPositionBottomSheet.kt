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
import com.duckduckgo.app.browser.databinding.BottomSheetQuickSetupAddressBarPositionBinding
import com.duckduckgo.app.browser.omnibar.OmnibarType
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
class QuickSetupAddressBarPositionBottomSheet : BottomSheetDialogFragment() {

    @Inject
    lateinit var appTheme: AppTheme

    private lateinit var initialSelection: OmnibarType
    private var showSplitOption: Boolean = false
    private var currentSelection: OmnibarType = OmnibarType.SINGLE_TOP

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialSelection = OmnibarType.valueOf(
            requireArguments().getString(ARG_INITIAL_SELECTION) ?: OmnibarType.SINGLE_TOP.name,
        )
        showSplitOption = requireArguments().getBoolean(ARG_SHOW_SPLIT_OPTION, false)
        currentSelection = savedInstanceState
            ?.getString(STATE_CURRENT_SELECTION)
            ?.let(OmnibarType::valueOf)
            ?: initialSelection
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding = BottomSheetQuickSetupAddressBarPositionBinding.inflate(inflater, container, false)

        with(binding.addressBarPicker) {
            setLightMode(appTheme.isLightModeEnabled())
            isSplitOptionVisible = showSplitOption
            setSelection(currentSelection)
            setOnSelectionChangedListener { selected ->
                currentSelection = selected
                setSelection(selected, animate = true)
            }
        }
        binding.quickSetupAddressBarPositionCaption.text =
            getString(BrowserR.string.quickSetupAddressBarPositionCaption).html(requireContext())
        binding.quickSetupAddressBarPositionDoneButton.setOnClickListener {
            setFragmentResult(REQUEST_KEY, bundleOf(RESULT_KEY_SELECTED_POSITION to currentSelection.name))
            dismiss()
        }
        binding.quickSetupAddressBarPositionCloseButton.setOnClickListener {
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
        outState.putString(STATE_CURRENT_SELECTION, currentSelection.name)
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
        private const val ARG_INITIAL_SELECTION = "initialSelection"
        private const val ARG_SHOW_SPLIT_OPTION = "showSplitOption"
        private const val STATE_CURRENT_SELECTION = "currentSelection"

        const val TAG = "QuickSetupAddressBarPositionBottomSheetFragment"

        /** Fragment-result request key. The host registers a listener under this key. */
        const val REQUEST_KEY = "QuickSetupAddressBarPositionResult"

        /** Bundle key for the selected [OmnibarType] (stored as `OmnibarType.name`). */
        const val RESULT_KEY_SELECTED_POSITION = "selectedPosition"

        fun newInstance(
            initialSelection: OmnibarType,
            showSplitOption: Boolean,
        ): QuickSetupAddressBarPositionBottomSheet {
            return QuickSetupAddressBarPositionBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_INITIAL_SELECTION, initialSelection.name)
                    putBoolean(ARG_SHOW_SPLIT_OPTION, showSplitOption)
                }
            }
        }
    }
}
