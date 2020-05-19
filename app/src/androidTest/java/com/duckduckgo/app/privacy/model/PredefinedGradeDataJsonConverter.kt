/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.privacy.model

import com.duckduckgo.app.privacy.model.PredefinedGradeDataJsonConverter.GradeTestCase.Input
import com.duckduckgo.app.privacy.model.PredefinedGradeDataJsonConverter.GradeTestCase.Tracker
import com.duckduckgo.app.trackerdetection.model.Entity

class PredefinedGradeDataJsonConverter {

    class GradeTestCase(val expected: Grade.Scores.ScoresAvailable, val input: Input, val url: String) {

        class Input(
            val https: Boolean,
            val httpsAutoUpgrade: Boolean,
            val parentEntity: Entity?,
            val privacyScore: Int?,
            val trackers: Array<Tracker>
        )

        class Tracker(val blocked: Boolean, val parentEntity: Entity)
    }

    class JsonGradeTestCase(val expected: Grade.Scores.ScoresAvailable, val input: JsonInput, val url: String) {

        class JsonInput(
            val https: Boolean,
            val httpsAutoUpgrade: Boolean,
            val parentEntity: String?,
            val privacyScore: Int?,
            val trackers: Array<JsonTracker>,
            val parentTrackerPrevalence: Double?
        )

        class JsonTracker(val blocked: Boolean, val parentEntity: String, val prevalence: Double?)
    }
}

fun PredefinedGradeDataJsonConverter.JsonGradeTestCase.JsonInput.toInput(): Input {
    val trackerList = trackers.map {
        Tracker(it.blocked, entity(it.parentEntity, it.prevalence))
    }
    return Input(
        https,
        httpsAutoUpgrade,
        entity(parentEntity, parentTrackerPrevalence),
        privacyScore,
        trackerList.toTypedArray()
    )
}

fun PredefinedGradeDataJsonConverter.JsonGradeTestCase.toGradeTestCase(): PredefinedGradeDataJsonConverter.GradeTestCase {
    return PredefinedGradeDataJsonConverter.GradeTestCase(expected, input.toInput(), url)
}

private fun entity(name: String?, prevalence: Double?): Entity {
    if (name == null) return TestEntity("", "", 0.0)
    return TestEntity(name, name, prevalence ?: 0.0)
}
