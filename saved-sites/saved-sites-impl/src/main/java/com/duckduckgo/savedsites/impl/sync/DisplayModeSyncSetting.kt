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

package com.duckduckgo.savedsites.impl.sync

import android.content.*
import android.util.*
import android.widget.*
import android.widget.CompoundButton.OnCheckedChangeListener
import androidx.lifecycle.*
import com.duckduckgo.anvil.annotations.*
import com.duckduckgo.di.scopes.*
import com.duckduckgo.mobile.android.ui.viewbinding.*
import com.duckduckgo.saved.sites.impl.databinding.*
import dagger.android.support.*
import javax.inject.*

@InjectWith(ViewScope::class)
class DisplayModeSyncSetting @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: DisplayModeViewModel.Factory

    private val binding: ViewSyncSettingDisplayModeBinding by viewBinding()

    private val viewModel: DisplayModeViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[DisplayModeViewModel::class.java]
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.syncSettingsOptionFavourites.setOnCheckedChangeListener(object : OnCheckedChangeListener {
            override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
                viewModel.onDisplayModechanged(isChecked)
            }
        })
    }

}
