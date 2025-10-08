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

package com.duckduckgo.common.ui.viewbinding

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.viewbinding.ViewBinding
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

inline fun <reified T : ViewBinding> Fragment.viewBinding() =
    FragmentViewBindingDelegate(T::class.java, this)

class FragmentViewBindingDelegate<T : ViewBinding>(
    bindingClass: Class<T>,
    fragment: Fragment,
) : ReadOnlyProperty<Fragment, T> {

    // LazyThreadSafetyMode.NONE because it will never be initialised from ore than one thread
    private val nullifyBindingHandler by
        lazy(LazyThreadSafetyMode.NONE) { Handler(Looper.getMainLooper()) }
    private var binding: T? = null

    private val bindMethod = bindingClass.getMethod("bind", View::class.java)

    init {
        fragment.viewLifecycleOwnerLiveData.observe(fragment) { lifecycleOwner ->
            lifecycleOwner.lifecycle.addObserver(
                @SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
                object : DefaultLifecycleObserver {
                    override fun onDestroy(owner: LifecycleOwner) {
                        nullifyBindingHandler.post { binding = null }
                        super.onDestroy(owner)
                    }
                },
            )
        }
    }

    override fun getValue(
        thisRef: Fragment,
        property: KProperty<*>,
    ): T {
        // onCreateView maybe be called between the onDestroyView and the next Main thread run-loop.
        // Because nullifyBindingHandler has to post to null the binding, it may happen that
        // [binding]
        // refers to the previous fragment view. When that happens, ie. bindings's root view does
        // not match
        // the current fragment, we just null the [binding] here too
        if (binding != null && binding?.root !== thisRef.view) {
            binding = null
        }

        binding?.let {
            return it
        }

        val lifecycle = thisRef.viewLifecycleOwner.lifecycle
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.INITIALIZED)) {
            error("Cannot access view bindings, View lifecycle is ${lifecycle.currentState}")
        }

        binding = bindMethod.invoke(null, thisRef.requireView()).cast<T>()

        return binding!!
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T> Any.cast(): T = this as T
