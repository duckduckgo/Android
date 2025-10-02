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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.duckduckgo.di.DaggerSet
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.navigation.api.GlobalActivityStarter.DeeplinkActivityParams
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import logcat.logcat
import java.lang.IllegalArgumentException
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class GlobalActivityStarterImpl @Inject constructor(
    private val activityMappers: DaggerSet<GlobalActivityStarter.ParamToActivityMapper>,
) : GlobalActivityStarter {
    override fun start(context: Context, params: GlobalActivityStarter.ActivityParams, options: Bundle?) {
        startIntent(context, params)?.let {
            logcat { "Activity $it for params $params found" }
            context.startActivity(it, options)
        } ?: throw IllegalArgumentException("Activity for params $params not found")
    }

    override fun startIntent(context: Context, params: GlobalActivityStarter.ActivityParams): Intent? {
        val activityClass = activityMappers.firstNotNullOfOrNull {
            it.map(params)
        }

        return activityClass?.let {
            logcat { "Activity $it for params $params found" }

            Intent(context, it).apply {
                putExtra(ACTIVITY_SERIALIZABLE_PARAMETERS_ARG, params)
            }
        }
    }

    override fun start(
        context: Context,
        deeplinkActivityParams: DeeplinkActivityParams,
        options: Bundle?,
    ) {
        startIntent(context, deeplinkActivityParams)?.let {
            logcat { "Activity $it for params $deeplinkActivityParams found" }
            context.startActivity(it, options)
        } ?: throw IllegalArgumentException("Activity for params $deeplinkActivityParams not found")
    }

    override fun startIntent(
        context: Context,
        deeplinkActivityParams: DeeplinkActivityParams,
    ): Intent? {
        val activityParams: ActivityParams? = activityMappers.firstNotNullOfOrNull {
            it.map(deeplinkActivityParams)
        }

        return activityParams?.let {
            startIntent(context, it)
        }
    }
}

private const val ACTIVITY_SERIALIZABLE_PARAMETERS_ARG = "ACTIVITY_SERIALIZABLE_PARAMETERS_ARG"
