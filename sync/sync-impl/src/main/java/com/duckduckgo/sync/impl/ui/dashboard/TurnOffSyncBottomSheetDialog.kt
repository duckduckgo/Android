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

package com.duckduckgo.sync.impl.ui.dashboard

import android.app.Dialog
import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.ConstraintSet.BOTTOM
import androidx.constraintlayout.widget.ConstraintSet.END
import androidx.constraintlayout.widget.ConstraintSet.PARENT_ID
import androidx.constraintlayout.widget.ConstraintSet.START
import androidx.constraintlayout.widget.ConstraintSet.TOP
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.core.view.isInvisible
import androidx.fragment.app.activityViewModels
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.applyBottomSystemBarInsetPadding
import com.duckduckgo.common.ui.setRoundCorners
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.BottomSheetTurnOffSyncBinding
import com.duckduckgo.sync.impl.ui.devices.ParcelableDevice
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.duckduckgo.mobile.android.R as CommonR

class TurnOffSyncBottomSheetDialog : BottomSheetDialogFragment() {
    private val viewModel by activityViewModels<SyncActivityViewModel> {
        (requireActivity() as DuckDuckGoActivity).viewModelFactory
    }

    private val device: ParcelableDevice
        get() = requireNotNull(BundleCompat.getParcelable(requireArguments(), DEVICE_ARG, ParcelableDevice::class.java)) {
            "Missing fragment argument: '$DEVICE_ARG'"
        }

    private val isOnlyDevice: Boolean
        get() = requireArguments().getBoolean(IS_ONLY_DEVICE_ARG)

    private var _binding: BottomSheetTurnOffSyncBinding? = null
    private val binding get() = _binding!!

    private lateinit var removeDeviceConstraints: ConstraintSet
    private lateinit var turnOffConstraints: ConstraintSet

    override fun getTheme(): Int = CommonR.style.Widget_DuckDuckGo_BottomSheetDialog_EdgeToEdge

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.behavior.skipCollapsed = true
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.setOnShowListener { dialog.setRoundCorners() }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetTurnOffSyncBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        binding.turnOffSyncSheet.applyBottomSystemBarInsetPadding()
        configureConstraints()
        configureCopy()
        configureButtons()
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        render(deleteServerData = binding.deleteServerDataSwitch.isChecked, animate = false)
        binding.deleteServerDataSwitch.setOnCheckedChangeListener { _, isChecked ->
            render(deleteServerData = isChecked, animate = true)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun render(
        deleteServerData: Boolean,
        animate: Boolean,
    ) {
        val isRemovingDevice = isOnlyDevice && !deleteServerData
        val constraints = if (isRemovingDevice) removeDeviceConstraints else turnOffConstraints
        if (animate) {
            val root = binding.root.rootView as? ViewGroup ?: binding.root
            TransitionManager.beginDelayedTransition(root, createStateTransition())
        }
        constraints.applyTo(binding.turnOffSyncContent)
        renderButtons(deleteServerData, animate)
        binding.deleteServerDataSwitch.contentDescription = getString(
            if (isRemovingDevice) {
                R.string.sync_simplified_settings_turn_off_sheet_remove_item
            } else {
                R.string.sync_simplified_settings_turn_off_sheet_item
            },
        )
    }

    private fun renderButtons(
        deleteServerData: Boolean,
        animate: Boolean,
    ) {
        val (shownButton, hiddenButton) = with(binding) {
            if (deleteServerData) turnOffAndDeleteButton to turnOffButton else turnOffButton to turnOffAndDeleteButton
        }
        shownButton.isClickable = true
        hiddenButton.isClickable = false
        shownButton.animate().cancel()
        hiddenButton.animate().cancel()
        shownButton.isInvisible = false

        if (!animate) {
            shownButton.alpha = 1f
            hiddenButton.alpha = 0f
            hiddenButton.isInvisible = true
            return
        }
        shownButton.animate()
            .alpha(1f)
            .setDuration(STATE_TRANSITION_DURATION_MS)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()
        hiddenButton.animate()
            .alpha(0f)
            .setDuration(STATE_TRANSITION_DURATION_MS)
            .setInterpolator(FastOutSlowInInterpolator())
            .withEndAction { hiddenButton.isInvisible = true }
            .start()
    }

    private fun configureConstraints() {
        removeDeviceConstraints = ConstraintSet().apply { clone(binding.turnOffSyncContent) }
        turnOffConstraints = createTurnOffConstraints()
    }

    private fun configureCopy() {
        val deviceName = device.name
        binding.removeDeviceTitle.text = getString(R.string.sync_simplified_settings_turn_off_sheet_remove_title, deviceName)
        binding.removeDeviceBody.text = getString(R.string.sync_simplified_settings_turn_off_sheet_remove_body, deviceName)
        binding.turnOffButton.setText(
            if (isOnlyDevice) {
                R.string.sync_simplified_settings_turn_off_sheet_remove_primary_button
            } else {
                R.string.sync_simplified_settings_turn_off_sheet_primary_button
            },
        )
        binding.turnOffAndDeleteButton.setText(
            if (isOnlyDevice) {
                R.string.sync_simplified_settings_turn_off_sheet_primary_button_delete
            } else {
                R.string.sync_simplified_settings_turn_off_sheet_primary_button_delete_all
            },
        )
    }

    private fun configureButtons() {
        binding.closeButton.setOnClickListener { dismiss() }
        binding.turnOffButton.setOnClickListener {
            viewModel.onTurnOffThisDeviceClicked(device.toConnectedDevice())
            dismiss()
        }
        binding.turnOffAndDeleteButton.setOnClickListener {
            viewModel.onTurnOffAndDeleteServerDataClicked()
            dismiss()
        }
    }

    private fun createTurnOffConstraints() = ConstraintSet().apply {
        clone(removeDeviceConstraints)
        binding.removeDeviceViews.forEach { setVisibility(it.id, View.GONE) }
        binding.turnOffViews.forEach { setVisibility(it.id, View.VISIBLE) }

        val item = R.id.turnOffItemBackground
        clear(R.id.deleteServerDataIcon, END)
        connect(R.id.deleteServerDataIcon, START, item, START, 24.toPx())
        connect(R.id.deleteServerDataIcon, TOP, item, TOP)
        connect(R.id.deleteServerDataIcon, BOTTOM, item, BOTTOM)

        connect(R.id.deleteServerDataSwitch, END, item, END, 16.toPx())
        connect(R.id.deleteServerDataSwitch, TOP, item, TOP)
        connect(R.id.deleteServerDataSwitch, BOTTOM, item, BOTTOM)

        clear(R.id.removeDeviceItemHint, BOTTOM)
        connect(R.id.turnOffBody, BOTTOM, PARENT_ID, BOTTOM, 42.toPx())
    }

    private fun createStateTransition() = TransitionSet()
        .setOrdering(TransitionSet.ORDERING_TOGETHER)
        .addTransition(Fade(Fade.OUT).excludeChildren(binding.turnOffButtons, true))
        .addTransition(ChangeBounds().excludeChildren(binding.turnOffButtons, true))
        .addTransition(Fade(Fade.IN).excludeChildren(binding.turnOffButtons, true))
        .setDuration(STATE_TRANSITION_DURATION_MS)
        .setInterpolator(FastOutSlowInInterpolator())

    companion object {
        const val TAG = "TurnOffSyncBottomSheetDialog"

        private const val DEVICE_ARG = "device"
        private const val IS_ONLY_DEVICE_ARG = "isOnlyDevice"
        private const val STATE_TRANSITION_DURATION_MS = 300L

        fun newInstance(
            device: ParcelableDevice,
            isOnlyDevice: Boolean,
        ) = TurnOffSyncBottomSheetDialog().apply {
            arguments = bundleOf(DEVICE_ARG to device, IS_ONLY_DEVICE_ARG to isOnlyDevice)
        }
    }
}

private val BottomSheetTurnOffSyncBinding.removeDeviceViews
    get() = listOf(
        removeDeviceTitle,
        removeDeviceBody,
        removeDeviceDivider,
        removeDeviceItemIconBackground,
        removeDeviceItemTitle,
        removeDeviceItemHint,
    )

private val BottomSheetTurnOffSyncBinding.turnOffViews
    get() = listOf(
        turnOffTitle,
        turnOffItemBackground,
        turnOffItemTitle,
        turnOffItemBottomSpace,
        turnOffBody,
    )
