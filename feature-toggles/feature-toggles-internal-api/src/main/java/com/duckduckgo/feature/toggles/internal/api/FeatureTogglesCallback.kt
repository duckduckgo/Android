/*
 * Copyright (c) 2024 DuckDuckGo
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
package com.duckduckgo.feature.toggles.internal.api

/**
 * This interface exists to facilitate the implementation of ToggleImpl which contains logic inside an api module.
 * This is an internal implementation to thread the need between toggles-api and toggles-impl and should NEVER
 * be used publicly.
 */
interface FeatureTogglesCallback {

    /**
     * This method is called whenever a cohort is assigned to the FeatureToggle
     */
    fun onCohortAssigned(experimentName: String, cohortName: String, enrollmentDate: String)

    /**
     * @return `true` if the ANY of the remote feature targets match the device configuration, `false` otherwise
     */
    fun matchesToggleTargets(targets: List<Any>): Boolean
}
