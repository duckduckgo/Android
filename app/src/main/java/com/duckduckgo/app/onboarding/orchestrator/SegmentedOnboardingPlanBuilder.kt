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

package com.duckduckgo.app.onboarding.orchestrator

import com.duckduckgo.onboarding.api.LinearOnboardingPlan
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.Stay
import logcat.logcat
import javax.inject.Inject

/**
 * Composes the segmented onboarding plan, the treatment arm of
 * [com.duckduckgo.app.onboarding.SegmentedOnboardingExperimentManager].
 *
 * Each [build] creates a fresh [NewUserOnboardingPlanContext] and per-run [SuspendMemo]s, so a new
 * onboarding run never reads stale state.
 */
internal class SegmentedOnboardingPlanBuilder @Inject constructor(
    private val steps: NewUserOnboardingSteps,
    private val plans: NewUserOnboardingPlans,
) {

    fun build(
        onCompleted: suspend () -> Unit,
        onSkipped: suspend () -> Unit,
    ): LinearOnboardingPlan {
        val ctx = NewUserOnboardingPlanContext()
        val firstDialog = SuspendMemo { FirstDialog.INITIAL }
        return plans.rootPlan(
            ctx = ctx,
            onCompleted = onCompleted,
            onSkipped = onSkipped,
            steps = buildList {
                add(steps.introAnimationStep())
                add(steps.notificationPermissionStep())
                add(steps.initialStep(firstDialog))
                add(downloadReasonStep())
            },
        )
    }

    private fun downloadReasonStep(): NewUserOnboardingActivityStep {
        return NewUserOnboardingActivityStep(
            id = NewUserOnboardingStepIds.DOWNLOAD_REASON,
            pixelName = null,
            resolveDialog = { NewUserOnboardingActivityDialog.DownloadReason },
            transition = { event ->
                when {
                    event is NewUserOnboardingEvent.DownloadReasonConfirmed -> {
                        logcat { "Download reason confirmed: ${event.selection}" }
                        Stay // navigation to next steps to be implemented in following PRs
                    }
                    else -> Stay
                }
            },
        )
    }
}
