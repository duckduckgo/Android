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

import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.viewbinding.ViewBinding
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

inline fun <reified T : ViewBinding> AppCompatActivity.viewBinding() =
    ActivityViewBindingDelegate(T::class.java, this)

class ActivityViewBindingDelegate<T : ViewBinding>(
    bindingClass: Class<T>,
    val activity: AppCompatActivity
) : ReadOnlyProperty<AppCompatActivity, T> {

    private var binding: T? = null
    private val bindMethod = bindingClass.getMethod("inflate", LayoutInflater::class.java)

    override fun getValue(
        thisRef: AppCompatActivity,
        property: KProperty<*>
    ): T {
        binding?.let {
            return it
        }

        val lifecycle = thisRef.lifecycle
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.INITIALIZED)) {
            error("Cannot access viewBinding activity lifecycle is ${lifecycle.currentState}")
        }

        binding = bindMethod.invoke(null, thisRef.layoutInflater).cast<T>()

        return binding!!
    }
}

@Suppress("UNCHECKED_CAST") private fun <T> Any.cast(): T = this as T
