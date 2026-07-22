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

package com.duckduckgo.navigation.impl

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import com.duckduckgo.di.DaggerSet
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.navigation.api.GlobalActivityStarter.DeeplinkActivityParams
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import logcat.LogPriority.ERROR
import logcat.LogPriority.WARN
import logcat.logcat
import java.lang.IllegalArgumentException
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class GlobalActivityStarterImpl @Inject constructor(
    private val activityMappers: DaggerSet<GlobalActivityStarter.ParamToActivityMapper>,
) : GlobalActivityStarter {

    override fun start(context: Context, params: GlobalActivityStarter.ActivityParams, options: Bundle?) {
        val intent = startIntent(context, params)
        if (intent == null) {
            logcat(ERROR) { "No activity found for params $params" }
            throw IllegalArgumentException("Activity for params $params not found")
        }
        context.startActivity(intent, options)
    }

    override fun startIntent(context: Context, params: GlobalActivityStarter.ActivityParams): Intent? {
        return buildIntent(context, params)?.apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun buildIntent(context: Context, params: GlobalActivityStarter.ActivityParams): Intent? {
        val matchingClasses = activityMappers.mapNotNull { it.map(params) }
        if (matchingClasses.size > 1) {
            logcat(WARN) { "Multiple mappers found for ${params::class.java.simpleName}: $matchingClasses. First match will be used." }
        }
        val activityClass = matchingClasses.firstOrNull() ?: return null

        logcat { "Activity $activityClass for params $params found" }
        return Intent(context, activityClass).apply {
            putExtra(ACTIVITY_SERIALIZABLE_PARAMETERS_ARG, params)
        }
    }

    override fun start(
        context: Context,
        deeplinkActivityParams: DeeplinkActivityParams,
        options: Bundle?,
    ) {
        val intent = startIntent(context, deeplinkActivityParams)
        if (intent == null) {
            logcat(ERROR) { "No activity found for params $deeplinkActivityParams" }
            throw IllegalArgumentException("Activity for params $deeplinkActivityParams not found")
        }
        context.startActivity(intent, options)
    }

    override fun startIntent(
        context: Context,
        deeplinkActivityParams: DeeplinkActivityParams,
    ): Intent? {
        return buildIntent(context, deeplinkActivityParams)?.apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun buildIntent(context: Context, deeplinkActivityParams: DeeplinkActivityParams): Intent? {
        val activityParams: ActivityParams? = activityMappers.firstNotNullOfOrNull { it.map(deeplinkActivityParams) }
        return activityParams?.let { buildIntent(context, it) }
    }

    // context is used only to construct the Intent — FLAG_ACTIVITY_NEW_TASK is never added,
    // regardless of context type. Callers should pass the Activity that owns the launcher.
    override fun startForResult(context: Context, params: ActivityParams, launcher: ActivityResultLauncher<Intent>) {
        val intent = buildIntent(context, params)
        if (intent == null) {
            logcat(ERROR) { "No activity found for params $params" }
            throw IllegalArgumentException("Activity for params $params not found")
        }
        launcher.launch(intent)
    }

    override fun startForResult(context: Context, deeplinkActivityParams: DeeplinkActivityParams, launcher: ActivityResultLauncher<Intent>) {
        val intent = buildIntent(context, deeplinkActivityParams)
        if (intent == null) {
            logcat(ERROR) { "No activity found for params $deeplinkActivityParams" }
            throw IllegalArgumentException("Activity for params $deeplinkActivityParams not found")
        }
        launcher.launch(intent)
    }
}

private const val ACTIVITY_SERIALIZABLE_PARAMETERS_ARG = "ACTIVITY_SERIALIZABLE_PARAMETERS_ARG"
