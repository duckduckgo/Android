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
import androidx.fragment.app.setFragmentResult
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.databinding.BottomSheetRemoveWidgetInstructionsBinding
import com.duckduckgo.common.ui.applyBottomSystemBarInsetPadding
import com.duckduckgo.common.ui.setRoundCorners
import com.duckduckgo.common.ui.store.AppBrandDesignUpdateToggles
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeBucket
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@InjectWith(FragmentScope::class)
class RemoveWidgetInstructionsBottomSheet : BottomSheetDialogFragment() {

    @Inject
    lateinit var edgeToEdgeProvider: EdgeToEdgeProvider

    @Inject
    lateinit var appBrandDesignUpdateToggles: AppBrandDesignUpdateToggles

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding = BottomSheetRemoveWidgetInstructionsBinding.inflate(inflater, container, false)
        if (appBrandDesignUpdateToggles.radius().isEnabled()) {
            binding.root.applyRadiusOverlayToSheetBackground()
        }
        binding.removeWidgetInstructionsDoneButton.setOnClickListener { dismiss() }
        binding.removeWidgetInstructionsCloseButton.setOnClickListener { dismiss() }
        if (edgeToEdgeProvider.isEnabled(EdgeToEdgeBucket.BOTTOM_SHEETS)) {
            binding.root.applyBottomSystemBarInsetPadding()
        }
        return binding.root
    }

    override fun getTheme(): Int = if (edgeToEdgeProvider.isEnabled(EdgeToEdgeBucket.BOTTOM_SHEETS)) {
        com.duckduckgo.mobile.android.R.style.Widget_DuckDuckGo_BottomSheetDialog_EdgeToEdge
    } else {
        com.duckduckgo.mobile.android.R.style.Widget_DuckDuckGo_BottomSheetDialog
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.isDraggable = false
        dialog.setOnShowListener {
            dialog.setRoundCorners(
                CommonR.attr.daxOnboardingSheetRadius,
                CommonR.dimen.onboardingBottomSheetCornerRadius,
            )
        }
        return dialog
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (isAdded) {
            setFragmentResult(REQUEST_KEY, Bundle.EMPTY)
        }
        super.onDismiss(dialog)
    }

    companion object {
        const val TAG = "RemoveWidgetInstructionsBottomSheetFragment"

        /** Fragment-result request key. The host registers a listener under this key. */
        const val REQUEST_KEY = "RemoveWidgetInstructionsResult"
    }
}
