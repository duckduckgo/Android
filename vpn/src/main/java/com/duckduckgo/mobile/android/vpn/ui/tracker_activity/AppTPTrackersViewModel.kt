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

package com.duckduckgo.mobile.android.vpn.ui.tracker_activity

import androidx.lifecycle.ViewModel
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import javax.inject.Provider

class AppTPTrackersViewModel @Inject constructor(
    val deviceShieldPixels: DeviceShieldPixels,
    val appBuildConfig: AppBuildConfig
) : ViewModel() {

}

@ContributesMultibinding(AppScope::class)
class AppTPTrackersViewModelFactory
@Inject
constructor(private val viewModel: Provider<AppTPTrackersViewModel>) :
    ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(AppTPTrackersViewModel::class.java) ->
                    (viewModel.get() as T)
                else -> null
            }
        }
    }
}
