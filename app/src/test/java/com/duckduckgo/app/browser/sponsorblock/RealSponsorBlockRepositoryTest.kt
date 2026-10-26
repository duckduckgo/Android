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

package com.duckduckgo.app.browser.sponsorblock

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.sponsorblock.RealSponsorBlockRepository.Companion.hashPrefix
import com.duckduckgo.app.browser.sponsorblock.RealSponsorBlockRepository.Companion.parseSegments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealSponsorBlockRepositoryTest {

    @Test
    fun whenElevenCharacterIdThenValid() {
        assertTrue(YouTubeVideoId.isValid("dQw4w9WgXcQ"))
        assertTrue(YouTubeVideoId.isValid("a-b_c1D2e3F"))
    }

    @Test
    fun whenWrongLengthOrCharactersThenInvalid() {
        assertFalse(YouTubeVideoId.isValid("tooShort"))
        assertFalse(YouTubeVideoId.isValid("dQw4w9WgXcQX"))
        assertFalse(YouTubeVideoId.isValid("dQw4w9WgX/Q"))
        assertFalse(YouTubeVideoId.isValid(""))
    }

    @Test
    fun whenHashPrefixThenFirstFourHexCharsOfSha256() {
        assertEquals("5f6b", hashPrefix("dQw4w9WgXcQ"))
    }

    @Test
    fun whenResponseHasSeveralVideosThenOnlyMatchingVideoSkipSegmentsReturnedSorted() {
        val json = """
            [
              {"videoID": "otherVideo1", "segments": [{"actionType": "skip", "segment": [1.0, 2.0]}]},
              {"videoID": "dQw4w9WgXcQ", "segments": [
                {"actionType": "skip", "segment": [30.5, 45.25]},
                {"actionType": "mute", "segment": [50.0, 60.0]},
                {"actionType": "skip", "segment": [0.0, 12.0]}
              ]}
            ]
        """.trimIndent()

        assertEquals(
            listOf(SponsorSegment(0, 12_000), SponsorSegment(30_500, 45_250)),
            parseSegments(json, "dQw4w9WgXcQ"),
        )
    }

    @Test
    fun whenResponseHasNoMatchingVideoThenEmpty() {
        val json = """[{"videoID": "otherVideo1", "segments": [{"actionType": "skip", "segment": [1.0, 2.0]}]}]"""

        assertEquals(emptyList<SponsorSegment>(), parseSegments(json, "dQw4w9WgXcQ"))
    }
}
