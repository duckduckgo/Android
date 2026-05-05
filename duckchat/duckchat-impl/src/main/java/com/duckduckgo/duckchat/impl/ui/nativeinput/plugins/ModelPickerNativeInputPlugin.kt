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

package com.duckduckgo.duckchat.impl.ui.nativeinput.plugins

import android.content.Context
import android.view.View
import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.nativeinput.NativeInputPlugin
import com.duckduckgo.duckchat.impl.nativeinput.PromptContribution
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.ModelPicker
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.ModelPickerView
import java.lang.ref.WeakReference
import javax.inject.Inject

@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = NativeInputPlugin::class,
    featureName = "pluginModelPickerNativeInput",
    parentFeatureName = "pluginPointNativeInput",
)
class ModelPickerNativeInputPlugin @Inject constructor() : NativeInputPlugin {

    override val containerId: Int = R.id.modelPickerContainer

    private var modelPicker: WeakReference<ModelPicker> = WeakReference(null)

    override fun createView(context: Context): View {
        return ModelPickerView(context).also { picker ->
            modelPicker = WeakReference(picker)
            picker.setPickerEnabled(true)
        }
    }

    override fun getPromptContribution(): PromptContribution? {
        val modelId = modelPicker.get()?.getSelectedModelId() ?: return null
        return PromptContribution.ModelSelection(modelId)
    }
}
