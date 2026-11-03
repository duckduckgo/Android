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

package com.duckduckgo.pir.impl.models

import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord.OptOutJobStatus

/**
 * A stored profile marked removed although the same scan found a profile with the same identifier, so only its name
 * or profile URL changed. Apple and Windows match profiles on the identifier alone, so they would have kept it.
 *
 * @property changedField Which part of the match key no longer matched.
 * @property previousStatus Status of the opt-out job before it was marked removed.
 */
data class RemovedProfileWithIdentifierFound(
    val changedField: ChangedField,
    val previousStatus: OptOutJobStatus,
) {
    enum class ChangedField {
        NAME,
        PROFILE_URL,
        NAME_AND_PROFILE_URL,
    }
}
