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

package dagger.android

import android.app.Activity
import android.app.Application
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import androidx.fragment.app.Fragment
import dagger.BindsInstance
import java.lang.RuntimeException

interface HasDaggerInjector {
    /**
     * This method returns the Dagger component factory given the key that identifies it.
     * This dagger component factory relates to Android types like Activities, Fragments, Services etc.
     * The component factory will be used by the [AndroidInjector] to do two things
     *  1. creating the dagger component
     *  2. inject the dependencies in the Android type (eg. Activity)
     */
    fun daggerFactoryFor(key: Class<*>): AndroidInjector.Factory<*>
}

interface AndroidInjector<T> {
    /** Injects the members of {@code instance}. */
    fun inject(instance: T)

    /**
     * Creates {@link AndroidInjector}s for a concrete subtype of a core Android type.
     *
     * @param <T> the concrete type to be injected
     */
    interface Factory<T> {
        /**
         * Creates an {@link AndroidInjector} for {@code instance}. This should be the same instance
         * that will be passed to {@link #inject(Object)}.
         */
        fun create(@BindsInstance instance: T): AndroidInjector<T>
    }

    companion object {
        /**
         * This method will:
         *  1. Uses the [Application] that should implement [HasDaggerInjector] to get the dagger component factory
         *   the (Android type) instance passed as parameter
         *  2. Use the factory to create the dagger component that relates to an Android type, eg. Activity
         *  3. Inject any dependency requested by the Android type
         */
        inline fun <reified T> inject(
            injector: Any,
            instance: T,
            mapKey: Class<*>? = null
        ) {
            if ((injector is HasDaggerInjector)) {
                (injector.daggerFactoryFor(mapKey ?: instance!!::class.java) as Factory<T>)
                    .create(instance)
                    .inject(instance)
            } else {
                throw RuntimeException("${injector.javaClass.canonicalName} class does not extend ${HasDaggerInjector::class.simpleName}")
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
            bindingKey: Class<*>? = null
        ) {
            AndroidInjector.inject(instance.applicationContext as Application, instance, bindingKey)
        }

        inline fun <reified T : Fragment> inject(
            instance: T,
            bindingKey: Class<*>? = null
        ) {
            AndroidInjector.inject(findHasDaggerInjectorForFragment(instance), instance, bindingKey)
        }

        inline fun <reified T : Service> inject(
            instance: T,
            bindingKey: Class<*>? = null
        ) {
            AndroidInjector.inject(instance.application, instance, bindingKey)
        }

        inline fun <reified T : BroadcastReceiver> inject(
            instance: T,
            context: Context,
            bindingKey: Class<*>? = null
        ) {
            AndroidInjector.inject(context.applicationContext as Application, instance, bindingKey)
        }

        /**
         * Injects the [fragment] if an associated [AndroidInjector] implementation is found, otherwise [IllegalArgumentException]
         * is thrown.
         *
         * The algorithm is the following:
         * * walks the parent fragment hierarchy until if finds one that implements [HasDaggerInjector], else
         * * uses the [fragment]'s and returns it if it implements [HasDaggerInjector], else
         * * uses the [Application] and returns it if it implements [HasDaggerInjector], else
         * * throws [IllegalArgumentException]
         */
        fun findHasDaggerInjectorForFragment(fragment: Fragment): HasDaggerInjector {
            var parentFragment: Fragment? = fragment
            while (parentFragment?.parentFragment != null) {
                parentFragment = parentFragment.parentFragment

                if (parentFragment is HasDaggerInjector) {
                    return parentFragment
                }
            }
            val activity = fragment.activity
            if (activity is HasDaggerInjector) {
                return activity
            }

            activity?.application?.let { return it as HasDaggerInjector }

            throw IllegalArgumentException("No injector found for ${fragment.javaClass.canonicalName}")
        }
    }
}
