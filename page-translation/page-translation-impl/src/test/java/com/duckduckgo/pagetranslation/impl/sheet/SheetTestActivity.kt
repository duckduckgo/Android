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
package com.duckduckgo.pagetranslation.impl.sheet

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.duckduckgo.di.android.AndroidInjector
import com.duckduckgo.di.android.HasInjector
import com.duckduckgo.pagetranslation.impl.language.DeviceLanguages

class SheetTestActivity : FragmentActivity(), HasInjector {

    var deviceLanguages: DeviceLanguages = object : DeviceLanguages {
        override fun get(): List<String> = listOf("en")
        override fun displayName(languageCode: String): String = if (languageCode == "es") "Spanish" else languageCode
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(com.duckduckgo.mobile.android.R.style.Theme_DuckDuckGo_Light)
        super.onCreate(savedInstanceState)
    }

    override fun injectorFactoryFor(key: Class<*>): AndroidInjector.Factory<*, *> =
        object : AndroidInjector.Factory<LanguagePackSheetFragment, SheetInjector> {
            override fun create(instance: LanguagePackSheetFragment) = SheetInjector(deviceLanguages)
        }

    class SheetInjector(private val deviceLanguages: DeviceLanguages) : AndroidInjector<LanguagePackSheetFragment> {
        override fun inject(instance: LanguagePackSheetFragment) {
            instance.deviceLanguages = deviceLanguages
        }
    }
}
