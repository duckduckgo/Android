/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.common.utils.extensions

import android.os.Build
import android.os.Bundle
import android.widget.EditText
import androidx.fragment.app.Fragment
import java.io.Serializable

fun Fragment.showKeyboard(editText: EditText) = activity?.showKeyboard(editText)

fun Fragment.hideKeyboard(editText: EditText) = activity?.hideKeyboard(editText)

inline fun <reified T : Serializable> Bundle.getSerializable(name: String): T? =
    if (Build.VERSION.SDK_INT >= 33) {
        getSerializable(name, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getSerializable(name) as? T
    }
