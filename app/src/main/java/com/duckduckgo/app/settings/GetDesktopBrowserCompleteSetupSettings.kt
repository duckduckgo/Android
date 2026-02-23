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

package com.duckduckgo.app.settings

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.desktopbrowser.GetDesktopBrowserActivityParams
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.listitem.TwoLineListItem
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.settings.api.CompleteSetupSettingsPlugin
import com.duckduckgo.settings.api.SettingsPageFeature
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@ContributesMultibinding(ActivityScope::class)
@PriorityKey(200)
class GetDesktopBrowserCompleteSetupSettings @Inject constructor(
    private val settingsPageFeature: SettingsPageFeature,
    private val settingsDataStore: SettingsDataStore,
    private val activity: AppCompatActivity,
    private val globalActivityStarter: GlobalActivityStarter,
    private val dispatchers: DispatcherProvider,
    private val pixel: Pixel,
) : CompleteSetupSettingsPlugin {

    override fun getView(context: Context): View {
        return TwoLineListItem(context = activity).apply {
            setPrimaryText(activity.getString(R.string.getDesktopBrowserSettingItemTitle))
            setSecondaryText(activity.getString(R.string.getDesktopBrowserSettingItemSubtitleTitle))
            setLeadingIconResource(CommonR.drawable.ic_device_laptop_install_color_24)

            configureOverflowMenu()

            gone()
        }.apply {
            showIfEnabledAndNotDismissed()
        }
    }

    private fun TwoLineListItem.showIfEnabledAndNotDismissed() {
        activity.lifecycleScope.launch {
            val canShowItem = withContext(dispatchers.io()) {
                settingsPageFeature.newDesktopBrowserSettingEnabled().isEnabled() &&
                    !settingsDataStore.getDesktopBrowserSettingDismissed
            }

            if (canShowItem) {
                pixel.fire(AppPixelName.GET_DESKTOP_BROWSER_COMPLETE_SETUP_IMPRESSION)

                activity.lifecycle.addObserver(
                    @SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
                    object : DefaultLifecycleObserver {
                        override fun onResume(owner: LifecycleOwner) {
                            activity.lifecycleScope.launch {
                                val isDismissed = withContext(dispatchers.io()) {
                                    settingsDataStore.getDesktopBrowserSettingDismissed
                                }
                                if (isDismissed) {
                                    gone()
                                }
                            }
                        }
                    },
                )

                val intent = globalActivityStarter.startIntent(
                    activity,
                    GetDesktopBrowserActivityParams(
                        source = GetDesktopBrowserActivityParams.Source.COMPLETE_SETUP,
                    ),
                ) ?: return@launch

                setOnClickListener {
                    pixel.fire(
                        AppPixelName.GET_DESKTOP_BROWSER_CLICKED,
                        mapOf(GET_DESKTOP_BROWSER_SOURCE_PIXEL_PARAM to GET_DESKTOP_BROWSER_SOURCE_COMPLETE_SETUP),
                    )
                    context.startActivity(intent)
                }

                show()
            }
        }
    }

    private fun TwoLineListItem.configureOverflowMenu() {
        showTrailingIcon()
        setTrailingIconClickListener { overflowView ->
            val layoutInflater = LayoutInflater.from(context)
            val popupMenu = buildPopupMenu(this, layoutInflater)
            popupMenu.show(this, overflowView)
        }
    }

    private fun buildPopupMenu(
        rootView: View,
        layoutInflater: LayoutInflater,
    ): PopupMenu {
        val popupMenu = PopupMenu(layoutInflater, R.layout.popup_window_get_desktop_browser_menu)
        val hideButton = popupMenu.contentView.findViewById<View>(R.id.hide)

        popupMenu.apply {
            onMenuItemClicked(hideButton) {
                rootView.findViewTreeLifecycleOwner()?.lifecycleScope?.launch(dispatchers.main()) {
                    rootView.gone()

                    withContext(dispatchers.io()) {
                        pixel.fire(
                            AppPixelName.GET_DESKTOP_BROWSER_DISMISSED,
                            mapOf(GET_DESKTOP_BROWSER_SOURCE_PIXEL_PARAM to GET_DESKTOP_BROWSER_SOURCE_HIDE),
                        )
                        settingsDataStore.getDesktopBrowserSettingDismissed = true
                    }
                }
            }
        }

        return popupMenu
    }

    companion object {
        const val GET_DESKTOP_BROWSER_SOURCE_PIXEL_PARAM = "source"

        private const val GET_DESKTOP_BROWSER_SOURCE_COMPLETE_SETUP = "complete_setup"
        private const val GET_DESKTOP_BROWSER_SOURCE_HIDE = "hide"
    }
}
