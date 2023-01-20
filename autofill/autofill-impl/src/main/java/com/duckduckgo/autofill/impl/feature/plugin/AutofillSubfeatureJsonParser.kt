/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.autofill.impl.feature.plugin

import com.duckduckgo.di.DaggerSet
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import org.json.JSONObject
import timber.log.Timber

interface AutofillSubfeatureJsonParser {
    fun processSubfeatures(subfeatureJson: JSONObject)
}

@ContributesBinding(AppScope::class)
class RealAutofillSubfeatureJsonParser @Inject constructor(
    plugins: DaggerSet<AutofillSubfeaturePlugin>,
) : AutofillSubfeatureJsonParser {

    private val settings = plugins.sortedBy { it.settingName.value }

    override fun processSubfeatures(subfeatureJson: JSONObject) {
        subfeatureJson.keys().forEach { subfeatureName ->
            findSubfeatureHandler(subfeatureName)?.let { settingPlugin ->
                val rawJson = subfeatureJson.getJSONObject(subfeatureName).toString()
                Timber.v("-> Delegating %s subfeature to %s", subfeatureName, settingPlugin.javaClass.simpleName)
                settingPlugin.store(rawJson)
            }
        }
    }

    private fun findSubfeatureHandler(featureName: String) =
        settings.firstOrNull { featureName == it.settingName.value }.also { plugin ->
            if (plugin == null) {
                Timber.w("No plugin found for setting: %s", featureName)
            }
        }
}
