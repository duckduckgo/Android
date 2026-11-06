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
package com.duckduckgo.nextsteps.api

import android.content.Context
import android.view.View

/**
 * Entry point to the "Complete your setup" next steps feature: enrols the user in the experiment
 * that decides whether, and in which design, the next steps section is shown on the New Tab Page.
 */
interface NextSteps {
    /**
     * Enrols the user in the next steps experiment and assigns a cohort.
     */
    suspend fun enroll()

    /**
     * The view that renders the active next steps message for this user's cohort, or null when the
     * user is not enrolled in a treatment cohort.
     */
    suspend fun provideSectionView(context: Context): View?

    /**
     * True while this user is in a treatment cohort and a next steps message holds the New Tab Page slot, whether or not it
     * has rendered yet. Turns false once the section ends: every item done, the user dismissed it, or the message expired.
     */
    suspend fun isSectionActive(): Boolean
}
