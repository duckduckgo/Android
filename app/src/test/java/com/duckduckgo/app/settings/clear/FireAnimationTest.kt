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

package com.duckduckgo.app.settings.clear

import com.duckduckgo.app.browser.R
import com.duckduckgo.app.settings.clear.FireAnimation.HeroAbstract
import com.duckduckgo.app.settings.clear.FireAnimation.HeroAbstract.getAnimationForIndex
import com.duckduckgo.app.settings.clear.FireAnimation.HeroFire
import com.duckduckgo.app.settings.clear.FireAnimation.HeroWater
import com.duckduckgo.app.settings.clear.FireAnimation.Inferno
import com.duckduckgo.app.settings.clear.FireAnimation.None
import org.junit.Assert.assertEquals
import org.junit.Test

class FireAnimationTest {

    @Test
    fun whenGetOptionIndexThenInfernoMapsToZero() {
        assertEquals(0, Inferno.getOptionIndex())
    }

    @Test
    fun whenGetOptionIndexThenHeroFireMapsToOne() {
        assertEquals(1, HeroFire.getOptionIndex())
    }

    @Test
    fun whenGetOptionIndexThenHeroWaterMapsToTwo() {
        assertEquals(2, HeroWater.getOptionIndex())
    }

    @Test
    fun whenGetOptionIndexThenHeroAbstractMapsToThree() {
        assertEquals(3, HeroAbstract.getOptionIndex())
    }

    @Test
    fun whenGetOptionIndexThenNoneMapsToFour() {
        assertEquals(4, None.getOptionIndex())
    }

    @Test
    fun whenGetAnimationForIndexZeroThenInferno() {
        assertEquals(Inferno, 0.getAnimationForIndex())
    }

    @Test
    fun whenGetAnimationForIndexOneThenHeroFire() {
        assertEquals(HeroFire, 1.getAnimationForIndex())
    }

    @Test
    fun whenGetAnimationForIndexTwoThenHeroWater() {
        assertEquals(HeroWater, 2.getAnimationForIndex())
    }

    @Test
    fun whenGetAnimationForIndexThreeThenHeroAbstract() {
        assertEquals(HeroAbstract, 3.getAnimationForIndex())
    }

    @Test
    fun whenGetAnimationForIndexFourThenNone() {
        assertEquals(None, 4.getAnimationForIndex())
    }

    @Test
    fun whenGetAnimationForIndexNegativeThenFallsBackToHeroFire() {
        assertEquals(HeroFire, (-1).getAnimationForIndex())
    }

    @Test
    fun whenGetAnimationForIndexJustAboveRangeThenFallsBackToHeroFire() {
        assertEquals(HeroFire, 5.getAnimationForIndex())
    }

    @Test
    fun whenGetAnimationForIndexFarAboveRangeThenFallsBackToHeroFire() {
        assertEquals(HeroFire, 999.getAnimationForIndex())
    }

    @Test
    fun whenAnimationRoundTrippedThroughIndexThenReturnsSameAnimation() {
        listOf(Inferno, HeroFire, HeroWater, HeroAbstract, None).forEach { animation ->
            assertEquals(animation, animation.getOptionIndex().getAnimationForIndex())
        }
    }

    @Test
    fun whenAvailableFireAnimationsIncludesInfernoThenInfernoFirstFollowedByLegacyFour() {
        assertEquals(
            listOf(Inferno, HeroFire, HeroWater, HeroAbstract, None),
            availableFireAnimations(includesInferno = true),
        )
    }

    @Test
    fun whenAvailableFireAnimationsExcludesInfernoThenListIsLegacyFour() {
        assertEquals(
            listOf(HeroFire, HeroWater, HeroAbstract, None),
            availableFireAnimations(includesInferno = false),
        )
    }

    @Test
    fun whenDisplayLabelInfernoAndIncludesInfernoThenInfernoStringResource() {
        assertEquals(R.string.settingsHeroFireAnimation, Inferno.displayLabelResId(includesInferno = true))
    }

    @Test
    fun whenDisplayLabelInfernoAndExcludesInfernoThenInfernoStringResource() {
        assertEquals(R.string.settingsHeroFireAnimation, Inferno.displayLabelResId(includesInferno = false))
    }

    @Test
    fun whenDisplayLabelHeroFireAndIncludesInfernoThenInfernoClassicStringResource() {
        assertEquals(R.string.settingsHeroFireAnimationClassic, HeroFire.displayLabelResId(includesInferno = true))
    }

    @Test
    fun whenDisplayLabelHeroFireAndExcludesInfernoThenLegacyHeroFireStringResource() {
        assertEquals(R.string.settingsHeroFireAnimation, HeroFire.displayLabelResId(includesInferno = false))
    }

    @Test
    fun whenDisplayLabelHeroWaterAndIncludesInfernoThenHeroWaterStringResource() {
        assertEquals(R.string.settingsHeroWaterAnimation, HeroWater.displayLabelResId(includesInferno = true))
    }

    @Test
    fun whenDisplayLabelHeroWaterAndExcludesInfernoThenHeroWaterStringResource() {
        assertEquals(R.string.settingsHeroWaterAnimation, HeroWater.displayLabelResId(includesInferno = false))
    }

    @Test
    fun whenDisplayLabelHeroAbstractAndIncludesInfernoThenHeroAbstractStringResource() {
        assertEquals(R.string.settingsHeroAbstractAnimation, HeroAbstract.displayLabelResId(includesInferno = true))
    }

    @Test
    fun whenDisplayLabelHeroAbstractAndExcludesInfernoThenHeroAbstractStringResource() {
        assertEquals(R.string.settingsHeroAbstractAnimation, HeroAbstract.displayLabelResId(includesInferno = false))
    }

    @Test
    fun whenDisplayLabelNoneAndIncludesInfernoThenNoneStringResource() {
        assertEquals(R.string.settingsNoneAnimation, None.displayLabelResId(includesInferno = true))
    }

    @Test
    fun whenDisplayLabelNoneAndExcludesInfernoThenNoneStringResource() {
        assertEquals(R.string.settingsNoneAnimation, None.displayLabelResId(includesInferno = false))
    }

    @Test
    fun whenBrandDesignDialogOpenedWithInfernoThenPreselectsPositionOne() {
        assertEquals(1, brandDesignPreselectFor(Inferno))
    }

    @Test
    fun whenBrandDesignDialogOpenedWithHeroFireThenPreselectsPositionTwo() {
        assertEquals(2, brandDesignPreselectFor(HeroFire))
    }

    @Test
    fun whenBrandDesignDialogOpenedWithHeroWaterThenPreselectsPositionThree() {
        assertEquals(3, brandDesignPreselectFor(HeroWater))
    }

    @Test
    fun whenBrandDesignDialogOpenedWithHeroAbstractThenPreselectsPositionFour() {
        assertEquals(4, brandDesignPreselectFor(HeroAbstract))
    }

    @Test
    fun whenBrandDesignDialogOpenedWithNoneThenPreselectsPositionFive() {
        assertEquals(5, brandDesignPreselectFor(None))
    }

    @Test
    fun whenBrandDesignDialogSelectsPositionOneThenAnimationIsInferno() {
        assertEquals(Inferno, brandDesignSelectionFor(selectedItem = 1))
    }

    @Test
    fun whenBrandDesignDialogSelectsPositionTwoThenAnimationIsHeroFire() {
        assertEquals(HeroFire, brandDesignSelectionFor(selectedItem = 2))
    }

    @Test
    fun whenBrandDesignDialogSelectsPositionThreeThenAnimationIsHeroWater() {
        assertEquals(HeroWater, brandDesignSelectionFor(selectedItem = 3))
    }

    @Test
    fun whenBrandDesignDialogSelectsPositionFourThenAnimationIsHeroAbstract() {
        assertEquals(HeroAbstract, brandDesignSelectionFor(selectedItem = 4))
    }

    @Test
    fun whenBrandDesignDialogSelectsPositionFiveThenAnimationIsNone() {
        assertEquals(None, brandDesignSelectionFor(selectedItem = 5))
    }

    @Test
    fun whenLegacyDialogOpenedWithHeroFireThenPreselectsPositionOne() {
        assertEquals(1, HeroFire.getOptionIndex())
    }

    @Test
    fun whenLegacyDialogOpenedWithHeroWaterThenPreselectsPositionTwo() {
        assertEquals(2, HeroWater.getOptionIndex())
    }

    @Test
    fun whenLegacyDialogOpenedWithHeroAbstractThenPreselectsPositionThree() {
        assertEquals(3, HeroAbstract.getOptionIndex())
    }

    @Test
    fun whenLegacyDialogOpenedWithNoneThenPreselectsPositionFour() {
        assertEquals(4, None.getOptionIndex())
    }

    @Test
    fun whenLegacyDialogSelectsPositionOneThenAnimationIsHeroFire() {
        assertEquals(HeroFire, 1.getAnimationForIndex())
    }

    @Test
    fun whenLegacyDialogSelectsPositionTwoThenAnimationIsHeroWater() {
        assertEquals(HeroWater, 2.getAnimationForIndex())
    }

    @Test
    fun whenLegacyDialogSelectsPositionThreeThenAnimationIsHeroAbstract() {
        assertEquals(HeroAbstract, 3.getAnimationForIndex())
    }

    @Test
    fun whenLegacyDialogSelectsPositionFourThenAnimationIsNone() {
        assertEquals(None, 4.getAnimationForIndex())
    }

    private fun brandDesignPreselectFor(animation: FireAnimation): Int {
        val animations = availableFireAnimations(includesInferno = true)
        return animations.indexOf(animation).coerceAtLeast(0) + 1
    }

    private fun brandDesignSelectionFor(selectedItem: Int): FireAnimation {
        val animations = availableFireAnimations(includesInferno = true)
        return animations[selectedItem - 1]
    }
}
