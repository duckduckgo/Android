/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.browser.applinks

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalAppLaunchTest {

    private val intent = Intent(Intent.ACTION_VIEW, "example://details".toUri())
    private val trustedIntent = Intent(Intent.ACTION_VIEW, "example://details".toUri()).setPackage(CALLER)

    @Test
    fun whenNotTrustedAndConfirmationRequiredThenOriginalIntentLaunchedAfterPrompt() {
        val launch = plan(showConfirmation = true, trustedCallerIntent = null)

        assertSame(intent, launch.intent)
        assertTrue(launch.showConfirmation)
        assertFalse(launch.finishCustomTab)
    }

    @Test
    fun whenNotTrustedAndConfirmationNotRequiredThenNoPromptAndTabKept() {
        val launch = plan(showConfirmation = false, trustedCallerIntent = null)

        assertFalse(launch.showConfirmation)
        assertFalse(launch.finishCustomTab)
    }

    @Test
    fun whenNotTrustedAndMultipleActivitiesThenChooserUsed() {
        val launch = plan(activities = listOf(resolveInfo(OTHER), resolveInfo(CALLER)), trustedCallerIntent = null)

        assertTrue(launch.useChooser)
    }

    @Test
    fun whenMultipleActivitiesAndUseFirstActivityFoundThenNoChooser() {
        val launch = plan(activities = listOf(resolveInfo(OTHER), resolveInfo(CALLER)), useFirstActivityFound = true, trustedCallerIntent = null)

        assertFalse(launch.useChooser)
    }

    @Test
    fun whenTrustedSingleActivityThenTrustedIntentLaunchedWithoutPromptAndTabFinished() {
        val launch = plan(showConfirmation = true, trustedCallerIntent = TrustedCallerIntent(trustedIntent, listOf(resolveInfo(CALLER))))

        assertSame(trustedIntent, launch.intent)
        assertFalse(launch.useChooser)
        assertFalse(launch.showConfirmation)
        assertTrue(launch.finishCustomTab)
    }

    @Test
    fun whenTrustedAndFinishCustomTabDisabledThenTabKept() {
        val launch = plan(
            trustedCallerIntent = TrustedCallerIntent(trustedIntent, listOf(resolveInfo(CALLER))),
            finishCustomTabOnTrustedCallerLaunch = false,
        )

        assertFalse(launch.showConfirmation)
        assertFalse(launch.finishCustomTab)
    }

    @Test
    fun whenTrustedMultipleActivitiesThenChooserWithoutPromptAndTabKept() {
        val launch = plan(trustedCallerIntent = TrustedCallerIntent(trustedIntent, listOf(resolveInfo(CALLER), resolveInfo(CALLER))))

        assertTrue(launch.useChooser)
        assertFalse(launch.showConfirmation)
        assertFalse(launch.finishCustomTab)
    }

    private fun plan(
        activities: List<ResolveInfo> = listOf(resolveInfo(CALLER)),
        useFirstActivityFound: Boolean = false,
        showConfirmation: Boolean = true,
        trustedCallerIntent: TrustedCallerIntent?,
        finishCustomTabOnTrustedCallerLaunch: Boolean = true,
    ) = planExternalAppLaunch(
        intent = intent,
        activities = activities,
        useFirstActivityFound = useFirstActivityFound,
        showConfirmation = showConfirmation,
        trustedCallerIntent = trustedCallerIntent,
        finishCustomTabOnTrustedCallerLaunch = finishCustomTabOnTrustedCallerLaunch,
    )

    private fun resolveInfo(packageName: String): ResolveInfo =
        ResolveInfo().apply { activityInfo = ActivityInfo().apply { this.packageName = packageName } }

    private companion object {
        const val CALLER = "com.example.app"
        const val OTHER = "com.different.app"
    }
}
