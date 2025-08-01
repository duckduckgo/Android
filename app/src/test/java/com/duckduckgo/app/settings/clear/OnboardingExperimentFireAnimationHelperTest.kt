package com.duckduckgo.app.settings.clear

import android.annotation.SuppressLint
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.onboardingdesignexperiment.OnboardingDesignExperimentToggles
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@SuppressLint("DenyListedApi")
class OnboardingExperimentFireAnimationHelperTest {

    private val fakeOnboardingDesignExperimentToggles = FakeFeatureToggleFactory.create(OnboardingDesignExperimentToggles::class.java)
    private val helper = OnboardingExperimentFireAnimationHelper(fakeOnboardingDesignExperimentToggles)

    @Before
    fun setUp() {
        fakeOnboardingDesignExperimentToggles.self().setRawStoredState(State(false))
        fakeOnboardingDesignExperimentToggles.buckOnboarding().setRawStoredState(State(false))
        fakeOnboardingDesignExperimentToggles.bbOnboarding().setRawStoredState(State(false))
    }

    @Test
    fun `when non hero fire animation selected then return its res id`() {
        val selectedAnimation = FireAnimation.HeroAbstract
        val result = helper.getSelectedFireAnimationResId(selectedAnimation)
        assertEquals(R.raw.hero_abstract_airstream, result)
    }

    @Test
    fun `when hero fire animation selected and no experiments enabled return stock fire animation`() {
        val selectedAnimation = FireAnimation.HeroFire

        val result = helper.getSelectedFireAnimationResId(selectedAnimation)

        assertEquals(R.raw.hero_fire_inferno, result)
    }

    @Test
    fun `when hero fire animation selected and buck enabled then return buck experiment fire animation`() {
        fakeOnboardingDesignExperimentToggles.buckOnboarding().setRawStoredState(State(true))
        val selectedAnimation = FireAnimation.HeroFire

        val result = helper.getSelectedFireAnimationResId(selectedAnimation)

        assertEquals(R.raw.buck_experiment_fire, result)
    }

    @Test
    fun `when hero fire animation selected and bb enabled then return bb experiment fire animation`() {
        fakeOnboardingDesignExperimentToggles.bbOnboarding().setRawStoredState(State(true))
        val selectedAnimation = FireAnimation.HeroFire

        val result = helper.getSelectedFireAnimationResId(selectedAnimation)

        assertEquals(R.raw.bb_experiment_fire_optimised, result)
    }
}
