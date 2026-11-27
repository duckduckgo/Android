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

package com.duckduckgo.pagetranslation.impl.session

import com.duckduckgo.pagetranslation.impl.messaging.PageUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class RealTranslationBatchPlannerTest {
    private val planner = RealTranslationBatchPlanner()

    @Test
    fun translateTheViewportFirstThenTheRestEachInPageOrder() {
        val batches = planner.plan(listOf(plain(0, "a"), plain(1, "b", vp = true), plain(2, "c"), plain(3, "d", vp = true)))

        assertEquals(
            listOf(
                TranslationBatch(listOf(plain(1, "b", vp = true), plain(3, "d", vp = true)), isViewport = true),
                TranslationBatch(listOf(plain(0, "a"), plain(2, "c")), isViewport = false),
            ),
            batches,
        )
    }

    @Test
    fun startANewBatchWhenTheKindChanges() {
        val batches = planner.plan(listOf(plain(0, "a"), html(1, "<b>b</b>"), html(2, "c"), plain(3, "d")))

        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3)), batches.map { batch -> batch.units.map { it.id } })
    }

    @Test
    fun fillABatchUpToExactlyTheLimit() {
        val batches = planner.plan(listOf(plain(0, "a".repeat(1_500)), plain(1, "b".repeat(500)), plain(2, "c")))

        assertEquals(listOf(listOf(0, 1), listOf(2)), batches.map { batch -> batch.units.map { it.id } })
    }

    @Test
    fun startANewBatchWhenTheNextUnitWouldPassTheLimit() {
        val batches = planner.plan(listOf(plain(0, "a".repeat(1_500)), plain(1, "b".repeat(501))))

        assertEquals(listOf(listOf(0), listOf(1)), batches.map { batch -> batch.units.map { it.id } })
    }

    @Test
    fun putAUnitLongerThanTheLimitAloneWithoutSplittingIt() {
        val long = plain(1, "b".repeat(2_001))

        val batches = planner.plan(listOf(plain(0, "a"), long, plain(2, "c")))

        assertEquals(listOf(listOf(plain(0, "a")), listOf(long), listOf(plain(2, "c"))), batches.map { it.units })
    }

    @Test
    fun countHtmlWithoutItsTags() {
        val batches = planner.plan(listOf(html(0, "<p>" + "a".repeat(1_000) + "</p>"), html(1, "<a href=\"x\">" + "b".repeat(1_000) + "</a>")))

        assertEquals(listOf(listOf(0, 1)), batches.map { batch -> batch.units.map { it.id } })
    }

    @Test
    fun countTheFirstBatchAsTheViewportWhenNoUnitIsInIt() {
        val batches = planner.plan(listOf(plain(0, "a".repeat(2_000)), plain(1, "b")))

        assertEquals(listOf(true, false), batches.map { it.isViewport })
    }

    @Test
    fun markEveryViewportBatchAndNoOther() {
        val batches = planner.plan(listOf(plain(0, "a".repeat(2_000), vp = true), plain(1, "b", vp = true), plain(2, "c")))

        assertEquals(listOf(true, true, false), batches.map { it.isViewport })
    }

    @Test
    fun planNothingForAPageWithoutUnits() {
        assertEquals(emptyList<TranslationBatch>(), planner.plan(emptyList()))
    }

    private fun plain(id: Int, text: String, vp: Boolean = false) = PageUnit(id, text, isHtml = false, isInViewport = vp)

    private fun html(id: Int, text: String, vp: Boolean = false) = PageUnit(id, text, isHtml = true, isInViewport = vp)
}
