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

package com.duckduckgo.app.dev.settings.tabs

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.view.isVisible
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityNewTabTransitionDevSettingsBinding
import com.duckduckgo.app.browser.tabs.ContainerTransformSource
import com.duckduckgo.app.browser.tabs.ContainerTransformStartSize
import com.duckduckgo.app.browser.tabs.NewTabTransition
import com.duckduckgo.app.browser.tabs.NewTabTransitionSettings
import com.duckduckgo.app.browser.tabs.TabManagerCloseBehaviour
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class NewTabTransitionDevSettingsActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var newTabTransitionSettings: NewTabTransitionSettings

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    private val binding: ActivityNewTabTransitionDevSettingsBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableTransparentEdgeToEdge()
        setContentView(binding.root)
        configureEdgeToEdgeInsets()
        setupToolbar(binding.includeToolbar.toolbar)

        binding.transitionOverrideToggle.quietlySetIsChecked(newTabTransitionSettings.isOverrideEnabled) { _, isChecked ->
            newTabTransitionSettings.isOverrideEnabled = isChecked
            render()
        }
        binding.animateClosingTabsToggle.quietlySetIsChecked(newTabTransitionSettings.animateClosingTabs) { _, isChecked ->
            newTabTransitionSettings.animateClosingTabs = isChecked
        }
        binding.containerRoundedCornersToggle.quietlySetIsChecked(newTabTransitionSettings.containerTransformRoundedCorners) { _, isChecked ->
            newTabTransitionSettings.containerTransformRoundedCorners = isChecked
        }
        binding.transitionSelector.setOnClickListener { showTransitionPicker() }
        binding.containerSourceSelector.setOnClickListener { showContainerSourcePicker() }
        binding.containerStartSizeSelector.setOnClickListener { showContainerStartSizePicker() }
        binding.tabManagerCloseSelector.setOnClickListener { showTabManagerClosePicker() }
        render()
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.includeToolbar.appBarLayout)
        edgeToEdgeHandler.applyNavigationBarInsets(binding.contentLayout, drawBehindGestureNav = false)
    }

    private fun render() {
        binding.swipingTabsNote.isVisible = newTabTransitionSettings.isOverrideEnabled
        binding.transitionSelector.isEnabled = newTabTransitionSettings.isOverrideEnabled
        binding.transitionSelector.setSecondaryText(getString(newTabTransitionSettings.transition.label()))
        val isContainerTransform = newTabTransitionSettings.transition == NewTabTransition.CONTAINER_TRANSFORM
        val isBottomCentered = newTabTransitionSettings.containerTransformSource == ContainerTransformSource.BOTTOM_CENTERED
        binding.containerSourceSelector.isVisible = isContainerTransform
        binding.containerSourceSelector.isEnabled = newTabTransitionSettings.isOverrideEnabled
        binding.containerSourceSelector.setSecondaryText(getString(newTabTransitionSettings.containerTransformSource.label()))
        binding.containerStartSizeSelector.isVisible = isContainerTransform
        binding.containerStartSizeSelector.isEnabled = newTabTransitionSettings.isOverrideEnabled && isBottomCentered
        binding.containerStartSizeSelector.setSecondaryText(getString(newTabTransitionSettings.containerTransformStartSize.label()))
        binding.containerRoundedCornersToggle.isVisible = isContainerTransform
        binding.containerRoundedCornersToggle.isEnabled = newTabTransitionSettings.isOverrideEnabled
        binding.animateClosingTabsToggle.isEnabled = newTabTransitionSettings.isOverrideEnabled
        binding.tabManagerCloseSelector.isEnabled = newTabTransitionSettings.isOverrideEnabled
        binding.tabManagerCloseSelector.setSecondaryText(getString(newTabTransitionSettings.tabManagerCloseBehaviour.label()))
    }

    private fun showTransitionPicker() {
        val popup = PopupMenu(layoutInflater, R.layout.popup_window_new_tab_transition)
        val view = popup.contentView
        popup.apply {
            onMenuItemClicked(view.findViewById(R.id.sharedAxis)) { onTransitionSelected(NewTabTransition.SHARED_AXIS) }
            onMenuItemClicked(view.findViewById(R.id.containerTransform)) { onTransitionSelected(NewTabTransition.CONTAINER_TRANSFORM) }
        }
        popup.show(binding.root, binding.transitionSelector)
    }

    private fun onTransitionSelected(transition: NewTabTransition) {
        newTabTransitionSettings.transition = transition
        render()
    }

    private fun showContainerSourcePicker() {
        val popup = PopupMenu(layoutInflater, R.layout.popup_window_container_transform_source)
        val view = popup.contentView
        popup.apply {
            onMenuItemClicked(view.findViewById(R.id.toolbarIcons)) { onContainerSourceSelected(ContainerTransformSource.TOOLBAR_ICONS) }
            onMenuItemClicked(view.findViewById(R.id.bottomCentered)) { onContainerSourceSelected(ContainerTransformSource.BOTTOM_CENTERED) }
        }
        popup.show(binding.root, binding.containerSourceSelector)
    }

    private fun onContainerSourceSelected(source: ContainerTransformSource) {
        newTabTransitionSettings.containerTransformSource = source
        render()
    }

    private fun showContainerStartSizePicker() {
        val popup = PopupMenu(layoutInflater, R.layout.popup_window_container_transform_start_size)
        val view = popup.contentView
        popup.apply {
            onMenuItemClicked(view.findViewById(R.id.startSizeQuarter)) { onContainerStartSizeSelected(ContainerTransformStartSize.QUARTER) }
            onMenuItemClicked(view.findViewById(R.id.startSizeHalf)) { onContainerStartSizeSelected(ContainerTransformStartSize.HALF) }
            onMenuItemClicked(view.findViewById(R.id.startSizeThreeQuarters)) {
                onContainerStartSizeSelected(ContainerTransformStartSize.THREE_QUARTERS)
            }
        }
        popup.show(binding.root, binding.containerStartSizeSelector)
    }

    private fun onContainerStartSizeSelected(startSize: ContainerTransformStartSize) {
        newTabTransitionSettings.containerTransformStartSize = startSize
        render()
    }

    private fun showTabManagerClosePicker() {
        val popup = PopupMenu(layoutInflater, R.layout.popup_window_tab_manager_close_behaviour)
        val view = popup.contentView
        popup.apply {
            onMenuItemClicked(view.findViewById(R.id.slide)) { onTabManagerCloseSelected(TabManagerCloseBehaviour.SLIDE) }
            onMenuItemClicked(view.findViewById(R.id.hideImmediately)) { onTabManagerCloseSelected(TabManagerCloseBehaviour.HIDE_IMMEDIATELY) }
        }
        popup.show(binding.root, binding.tabManagerCloseSelector)
    }

    private fun onTabManagerCloseSelected(behaviour: TabManagerCloseBehaviour) {
        newTabTransitionSettings.tabManagerCloseBehaviour = behaviour
        render()
    }

    private fun NewTabTransition.label(): Int = when (this) {
        NewTabTransition.SHARED_AXIS -> R.string.newTabTransitionDevSettingsSharedAxis
        NewTabTransition.CONTAINER_TRANSFORM -> R.string.newTabTransitionDevSettingsContainerTransform
    }

    private fun ContainerTransformSource.label(): Int = when (this) {
        ContainerTransformSource.TOOLBAR_ICONS -> R.string.newTabTransitionDevSettingsContainerSourceToolbarIcons
        ContainerTransformSource.BOTTOM_CENTERED -> R.string.newTabTransitionDevSettingsContainerSourceBottomCentered
    }

    private fun ContainerTransformStartSize.label(): Int = when (this) {
        ContainerTransformStartSize.QUARTER -> R.string.newTabTransitionDevSettingsContainerStartSizeQuarter
        ContainerTransformStartSize.HALF -> R.string.newTabTransitionDevSettingsContainerStartSizeHalf
        ContainerTransformStartSize.THREE_QUARTERS -> R.string.newTabTransitionDevSettingsContainerStartSizeThreeQuarters
    }

    private fun TabManagerCloseBehaviour.label(): Int = when (this) {
        TabManagerCloseBehaviour.SLIDE -> R.string.newTabTransitionDevSettingsTabManagerSlide
        TabManagerCloseBehaviour.HIDE_IMMEDIATELY -> R.string.newTabTransitionDevSettingsTabManagerHideImmediately
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, NewTabTransitionDevSettingsActivity::class.java)
        }
    }
}
