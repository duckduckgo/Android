/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.global.dialog

import android.content.DialogInterface
import android.view.KeyEvent

class BackKeyListener(private val onBackPressed: () -> Unit) : DialogInterface.OnKeyListener {

    override fun onKey(
        dialog: DialogInterface?,
        keyCode: Int,
        event: KeyEvent?
    ): Boolean {
        if (isBackKey(keyCode, event)) {
            onBackPressed.invoke()
            dialog?.dismiss()
            return true
        }
        return false
    }

    private fun isBackKey(
        keyCode: Int,
        event: KeyEvent?
    ): Boolean {
        return (keyCode == KeyEvent.KEYCODE_BACK && event?.action == KeyEvent.ACTION_UP)
    }
}
