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

package com.duckduckgo.di.android

import android.app.Activity
import android.app.Application
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.findFragment
import dev.zacsweers.metro.Provides
import java.lang.RuntimeException

interface HasInjector {
    /**
     * This method returns the injector factory given the key that identifies it.
     * This injector factory relates to Android types like Activities, Fragments, Services etc.
     * The injector factory will be used by the [AndroidInjector] to do two things
     *  1. creating the injector
     *  2. inject the dependencies in the Android type (eg. Activity)
     */
    fun injectorFactoryFor(key: Class<*>): AndroidInjector.Factory<*, *>
}

interface AndroidInjector<T> {
    /** Injects the members of {@code instance}. */
    fun inject(instance: T)

    /**
     * Creates {@link AndroidInjector}s for a concrete subtype of a core Android type.
     *
     * @param <T> the concrete type to be injected
     */
    interface Factory<T, SubComponentType : AndroidInjector<T>> {
        /**
         * Creates an {@link AndroidInjector} for {@code instance}. This should be the same instance
         * that will be passed to {@link #inject(Object)}.
         */
        fun create(@Provides instance: T): SubComponentType
    }

    companion object {
        /**
         * This method will:
         *  1. Uses the [Application] that should implement [HasInjector] to get the injector factory
         *   the (Android type) instance passed as parameter
         *  2. Use the factory to create the injector that relates to an Android type, eg. Activity
         *  3. Inject any dependency requested by the Android type
         */
        inline fun <reified T, R : AndroidInjector<T>> inject(
            injector: Any,
            instance: T,
            mapKey: Class<*>? = null,
        ) {
            if ((injector is HasInjector)) {
                (injector.injectorFactoryFor(mapKey ?: instance!!::class.java) as Factory<T, R>)
                    .create(instance)
                    .run {
                        javaClass.getMethod("inject", instance!!::class.java).invoke(this, instance)
                    }
            } else {
                throw RuntimeException("${injector.javaClass.canonicalName} class does not extend ${HasInjector::class.simpleName}")
            }
        }
    }
}

/**
 * All the methods in this class are bridges to the [AndroidInjector.Companion.inject] method.
 * We will have one method overload per Android type like [Activity], [Fragment], [Service], etc
 *
 * The caller of the method can optionally pass the bindingKey parameter, that will be used by the
 * [AndroidInjector.Companion.inject] method to get the [AndroidInjector.Factory].
 */
class AndroidInjection {
    companion object {
        inline fun <reified T : Activity> inject(
            instance: T,
            bindingKey: Class<*>? = null,
        ) {
            AndroidInjector.inject(instance.applicationContext as Application, instance, bindingKey)
        }

        inline fun <reified T : Fragment> inject(
            instance: T,
            bindingKey: Class<*>? = null,
        ) {
            AndroidInjector.inject(findInjectorForFragment(instance), instance, bindingKey)
        }

        inline fun <reified T : Service> inject(
            instance: T,
            bindingKey: Class<*>? = null,
        ) {
            AndroidInjector.inject(instance.application, instance, bindingKey)
        }

        inline fun <reified T : BroadcastReceiver> inject(
            instance: T,
            context: Context,
            bindingKey: Class<*>? = null,
        ) {
            AndroidInjector.inject(context.applicationContext as Application, instance, bindingKey)
        }

        inline fun <reified T : View> inject(
            instance: T,
            bindingKey: Class<*>? = null,
        ) {
            AndroidInjector.inject(findInjectorForView(instance), instance, bindingKey)
        }

        /**
         * Returns the [HasInjector] that should inject the [fragment], otherwise [IllegalArgumentException] is thrown.
         *
         * The algorithm is the following:
         * * walks the parent fragment hierarchy until it finds one that implements [HasInjector], else
         * * uses the [fragment]'s activity and returns it if it implements [HasInjector], else
         * * uses the [Application] and returns it if it implements [HasInjector], else
         * * throws [IllegalArgumentException]
         */
        fun findInjectorForFragment(fragment: Fragment): HasInjector {
            var parentFragment: Fragment? = fragment
            while (parentFragment?.parentFragment != null) {
                parentFragment = parentFragment.parentFragment

                if (parentFragment is HasInjector) {
                    return parentFragment
                }
            }
            val activity = fragment.activity
            if (activity is HasInjector) {
                return activity
            }

            activity?.application?.let { return it as HasInjector }

            throw IllegalArgumentException("No injector found for ${fragment.javaClass.canonicalName}")
        }

        /**
         * Returns the [HasInjector] that should inject the [view], otherwise [IllegalArgumentException] is thrown.
         *
         * The algorithm is the following:
         * 1. Starting from the fragment where this view is attached, walk the parent fragment hierarchy and return the first
         * [InjectableFragment] found.
         * 2. If there is no such fragment, find the activity where this view is attached. If the activity implements [HasInjector],
         * return it.
         * 3. If above steps are unsuccessful throw [IllegalArgumentException].
         */
        fun findInjectorForView(view: View): HasInjector {
            var fragment: Fragment? = try {
                view.findFragment()
            } catch (e: IllegalStateException) {
                // This view is not attached to a fragment.
                null
            }
            while (fragment != null) {
                if (fragment is InjectableFragment) {
                    return fragment
                }
                fragment = fragment.parentFragment
            }

            var context = view.context
            while (context is ContextWrapper) {
                if (context is Activity && context is HasInjector) {
                    return context
                }
                context = context.baseContext
            }

            throw IllegalArgumentException("No injector found for ${view.javaClass.canonicalName}")
        }
    }
}
