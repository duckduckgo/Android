/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.ui.viewbinding

import android.app.Dialog
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

inline fun <reified T : ViewBinding> Dialog.viewBinding() = DialogBindingDelegate(T::class.java, this)

class DialogBindingDelegate<T : ViewBinding>(
    bindingClass: Class<T>,
    dialog: Dialog
) : ReadOnlyProperty<Dialog, T> {
    private val binding: T = try {
        val inflateMethod = bindingClass.getMethod("inflate", LayoutInflater::class.java, ViewGroup::class.java, Boolean::class.javaPrimitiveType)
        inflateMethod.invoke(null, LayoutInflater.from(dialog.context), null, false).cast()
    } catch (e: NoSuchMethodException) {
        val inflateMethod = bindingClass.getMethod("inflate", LayoutInflater::class.java, ViewGroup::class.java)
        inflateMethod.invoke(null, LayoutInflater.from(dialog.context), null).cast()
    }

    override fun getValue(thisRef: Dialog, property: KProperty<*>): T {
        return binding
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T> Any.cast(): T = this as T
