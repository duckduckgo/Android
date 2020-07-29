/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.global.view

import android.content.Context
import android.os.Bundle
import android.view.View
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.cta.ui.DaxFireCta
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.android.synthetic.main.include_dax_dialog_cta.*
import kotlinx.android.synthetic.main.sheet_fire_clear_data.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class FireDialog2(
    context: Context,
    private val clearPersonalDataAction: ClearPersonalDataAction
) : BottomSheetDialog(context, R.style.FireDialog) {

    var clearStarted: (() -> Unit) = {}
    var clearComplete: (() -> Unit) = {}

    var localContentView: View = View.inflate(context, R.layout.sheet_fire_clear_data_2, null)

    init {
        setContentView(localContentView)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (localContentView.parent as View).setBackgroundColor(context.resources.getColor(android.R.color.transparent))

        val fireCta = DaxFireCta.FireCta()
        fireCta.showCta(daxCtaContainer)

        clearAllOption.setOnClickListener {
            dismiss()
            clearStarted()

            GlobalScope.launch {
                clearPersonalDataAction.clearTabsAndAllDataAsync(appInForeground = true, shouldFireDataClearPixel = true)
                clearPersonalDataAction.setAppUsedSinceLastClearFlag(false)
                clearPersonalDataAction.killAndRestartProcess()
            }
        }

        cancelOption.setOnClickListener {
            dismiss()
        }
    }
}
