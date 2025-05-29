package com.duckduckgo.app.settings.clear

import com.duckduckgo.app.browser.R
import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingExperimentFireAnimationHelperTest {

    private val helper = OnboardingExperimentFireAnimationHelper()

    @Test
    fun `when hero fire animation selected then return buck experiment fire animation`() {
        val selectedAnimation = FireAnimation.HeroFire
        val result = helper.getSelectedFireAnimationResId(selectedAnimation)
        assertEquals(R.raw.buck_experiment_fire, result)
    }

    @Test
    fun `when non hero fire animation selected then return its res id`() {
        val selectedAnimation = FireAnimation.HeroAbstract
        val result = helper.getSelectedFireAnimationResId(selectedAnimation)
        assertEquals(R.raw.hero_abstract_airstream, result)
    }
}
