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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.RectEvaluator
import android.app.Dialog
import android.graphics.Rect
import android.os.Bundle
import android.os.Parcelable
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.transition.TransitionValues
import android.transition.Visibility
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.setFragmentResult
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.duckduckgo.common.ui.applyBottomSystemBarInsetPadding
import com.duckduckgo.common.ui.setRoundCorners
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.BottomSheetTurnOffSyncBinding
import com.duckduckgo.sync.impl.ui.dashboard.SyncDeviceListItem.SyncedDevice
import com.duckduckgo.sync.impl.ui.devices.ParcelableDevice
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.parcelize.Parcelize
import com.duckduckgo.mobile.android.R as CommonR

class TurnOffSyncBottomSheetDialog : BottomSheetDialogFragment() {
    private val args: Args
        get() = requireNotNull(BundleCompat.getParcelable(requireArguments(), FRAGMENT_ARGS, Args::class.java)) {
            "Missing fragment argument: '$FRAGMENT_ARGS'"
        }

    private var _binding: BottomSheetTurnOffSyncBinding? = null
    private val binding get() = _binding!!

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
        configureCopy()
        configureDevices()
        configureButtons()
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        if (savedInstanceState == null) {
            binding.deleteServerDataSwitch.isChecked = args.allDevices.size <= 1
        }
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
        if (deleteServerData) {
            binding.devicesRecycler.scrollToPosition(0)
        }
        if (animate) {
            val root = binding.root.rootView as? ViewGroup ?: binding.root
            TransitionManager.beginDelayedTransition(root, createStateTransition())
        }
        binding.removeDeviceViews.forEach { it.isVisible = !deleteServerData }
        binding.turnOffViews.forEach { it.isVisible = deleteServerData }
        renderButtons(deleteServerData, animate)
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

    private fun configureCopy() {
        val deviceName = args.thisDevice.name
        binding.removeDeviceTitle.text = getString(R.string.sync_simplified_settings_turn_off_sheet_remove_title, deviceName)
        binding.removeDeviceBody.text = getString(R.string.sync_simplified_settings_turn_off_sheet_remove_body, deviceName)
    }

    private fun configureDevices() {
        val adapter = SyncedDeviceAdapter()
        binding.devicesRecycler.adapter = adapter
        adapter.submitList(args.allDevices.map { SyncedDevice(it.toConnectedDevice()) })
    }

    private fun configureButtons() {
        binding.closeButton.setOnClickListener { dismiss() }
        binding.turnOffButton.setOnClickListener { selectChoice(Choice.TurnOffThisDevice(args.thisDevice)) }
        binding.turnOffAndDeleteButton.setOnClickListener { selectChoice(Choice.TurnOffAndDeleteServerData) }
    }

    private fun selectChoice(choice: Choice) {
        setFragmentResult(REQUEST_KEY, bundleOf(RESULT_KEY_CHOICE to choice))
        dismiss()
    }

    private fun createStateTransition() = TransitionSet()
        .setOrdering(TransitionSet.ORDERING_TOGETHER)
        .addTransition(
            Fade(Fade.OUT)
                .excludeChildren(binding.turnOffButtons, true)
                .excludeTarget(binding.devicesSection, true)
                .excludeChildren(binding.devicesSection, true),
        )
        .addTransition(
            ChangeBounds()
                .excludeChildren(binding.turnOffButtons, true)
                .excludeChildren(binding.devicesSection, true),
        )
        .addTransition(
            Fade(Fade.IN)
                .excludeChildren(binding.turnOffButtons, true)
                .excludeTarget(binding.devicesSection, true)
                .excludeChildren(binding.devicesSection, true),
        )
        .addTransition(VerticalClipReveal().addTarget(binding.devicesSection))
        .setDuration(STATE_TRANSITION_DURATION_MS)
        .setInterpolator(FastOutSlowInInterpolator())

    sealed interface Choice : Parcelable {
        @Parcelize
        data class TurnOffThisDevice(val device: ParcelableDevice) : Choice

        @Parcelize
        data object TurnOffAndDeleteServerData : Choice
    }

    @Parcelize
    class Args(
        val thisDevice: ParcelableDevice,
        val allDevices: List<ParcelableDevice>,
    ) : Parcelable

    companion object {
        const val TAG = "TurnOffSyncBottomSheetDialog"
        const val REQUEST_KEY = "TurnOffSyncResult"
        const val RESULT_KEY_CHOICE = "choice"

        private const val FRAGMENT_ARGS = "${TAG}Args"
        private const val STATE_TRANSITION_DURATION_MS = 300L

        fun newInstance(
            thisDevice: ParcelableDevice,
            allDevices: List<ParcelableDevice>,
        ) = TurnOffSyncBottomSheetDialog().apply {
            arguments = Bundle().apply {
                putParcelable(FRAGMENT_ARGS, Args(thisDevice, allDevices))
            }
        }
    }
}

private val BottomSheetTurnOffSyncBinding.removeDeviceViews
    get() = listOf(
        removeDeviceTitle,
        removeDeviceBody,
    )

private val BottomSheetTurnOffSyncBinding.turnOffViews
    get() = listOf(
        turnOffTitle,
        turnOffBody,
        devicesSection,
    )

private class VerticalClipReveal : Visibility() {
    override fun onAppear(
        sceneRoot: ViewGroup,
        view: View,
        startValues: TransitionValues?,
        endValues: TransitionValues?,
    ): Animator = createClipAnimator(view, fromHeight = 0, toHeight = view.height)

    override fun onDisappear(
        sceneRoot: ViewGroup,
        view: View,
        startValues: TransitionValues?,
        endValues: TransitionValues?,
    ): Animator = createClipAnimator(view, fromHeight = view.height, toHeight = 0)

    private fun createClipAnimator(
        view: View,
        fromHeight: Int,
        toHeight: Int,
    ): Animator {
        val from = Rect(0, 0, view.width, fromHeight)
        val to = Rect(0, 0, view.width, toHeight)
        view.clipBounds = from
        return ObjectAnimator.ofObject(view, "clipBounds", RectEvaluator(), from, to).apply {
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        view.clipBounds = null
                    }
                },
            )
        }
    }
}
