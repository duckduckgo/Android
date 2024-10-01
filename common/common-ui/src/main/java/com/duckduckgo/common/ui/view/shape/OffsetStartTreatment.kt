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
package com.duckduckgo.common.ui.view.shape

import com.google.android.material.shape.EdgeTreatment
import com.google.android.material.shape.ShapePath

class OffsetStartTreatment(private val other: EdgeTreatment, private val offsetPx: Int) :
    EdgeTreatment() {

    override fun getEdgePath(
        length: Float,
        center: Float,
        interpolation: Float,
        shapePath: ShapePath,
    ) {
        other.getEdgePath(length, offsetPx.toFloat(), interpolation, shapePath)
    }
}
