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

package com.duckduckgo.navigation.api

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import java.io.Serializable
import kotlin.jvm.Throws

/**
 * This is the Activity Starter.
 * Use this type to start Activities or get their start intent.
 *
 * Activities to launch are identified by their input [ActivityParams].
 *
 * ```kotlin
 * data class ExampleActivityParams(...) : ActivityParams
 *
 * globalActivityStarter.start(context, ExampleActivityParams(...))
 * ```
 */
interface GlobalActivityStarter {

    /**
     * Starts the activity given its [params].
     *
     * The activity can later retrieve the [Serializable] [params] using the extension function [Bundle.getActivityParams]
     *
     * @param params The activity parameters. They also identify the activity
     * @param context the context used to start the activity
     * @param options additional options for how the activity should be started, eg. scene transition animations
     *
     * @throws IllegalArgumentException when the Activity can't be found
     */
    @Throws(IllegalArgumentException::class)
    fun start(context: Context, params: ActivityParams, options: Bundle? = null)

    /**
     * Returns  the [Intent] that can be used to start the [Activity], given the [ActivityParams].
     * This method will generally be used to start the activity for result.
     *
     * @return the [Intent] that can be used to start the [Activity]
     */
    fun startIntent(context: Context, params: ActivityParams): Intent?

    /**
     * This is a marker class
     * Mark all data classes related to activity arguments with this type
     */
    interface ActivityParams : Serializable

    /**
     * Implement this mapper that will return [Activity] class for the given parameters.
     * Once implemented it, you need to contribute it as a multibinding using [ContributesMultibinding] into the [AppScope].
     * ```kotlin
     * @ContributesMultibinding(AppScope::class)
     * class ExampleParamToActivityMapper @Inject constructor(...) : ParamToActivityMapper {
     *   fun fun map(params: ActivityParams): Class<out AppCompatActivity>? {
     *     return if (params is ExampleActivityParams) {
     *       ExampleActivity::class.java
     *     }
     *     else {
     *       null
     *     }
     *   }
     * }
     * data class ExampleActivityParams(...) : ActivityParams
     *
     * class ExampleActivity() : DuckDuckGoActivity() {...}
     * ```
     *
     * Alternatively you can also use the [ContributeToActivityStarter] annotation to autogenerate the parap to activity mapper above.
     * ```kotlin
     * @ContributeToActivityStarter(ExampleActivityParams::class)
     * class ExampleActivity() : DuckDuckGoActivity() {...}
     * ```
     */
    interface ParamToActivityMapper {
        /**
         * @return the [Activity] class if it matches the desired parameters, otherwise return `null`
         */
        fun map(activityParams: ActivityParams): Class<out AppCompatActivity>?
    }
}

private const val ACTIVITY_SERIALIZABLE_PARAMETERS_ARG = "ACTIVITY_SERIALIZABLE_PARAMETERS_ARG"

/**
 * This is a convenience method to extract the typed parameters from the launched activity
 * ```kotlin
 * @ContributeToActivityStarter(ExampleActivityParams::class)
 * class ExampleActivity() : DuckDuckGoActivity() {
 *   fun onCreate(...) {
 *     val params = intent.getActivityParams(ExampleActivityParams::class)
 *     ...
 *   }
 * }
 * ```
 */
fun <T : Serializable?> Intent.getActivityParams(clazz: Class<T>): T? {
    val data = runCatching {
        getSerializableExtra(ACTIVITY_SERIALIZABLE_PARAMETERS_ARG)
    }.getOrNull()

    data?.let {
        if (clazz.isAssignableFrom(data.javaClass)) {
            return data as T
        }
    }

    return null
}
