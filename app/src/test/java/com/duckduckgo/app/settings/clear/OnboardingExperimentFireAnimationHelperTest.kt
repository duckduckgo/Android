package com.duckduckgo.app.settings.clear

import android.annotation.SuppressLint
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.cta.ui.Cta
import com.duckduckgo.app.onboardingdesignexperiment.OnboardingDesignExperimentManager
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@SuppressLint("DenyListedApi")
class OnboardingExperimentFireAnimationHelperTest {

    private lateinit var fakeOnboardingDesignExperimentManager: FakeOnboardDesignExperimentManager
    private lateinit var testee: OnboardingExperimentFireAnimationHelper

    @Before
    fun setUp() {
        fakeOnboardingDesignExperimentManager = FakeOnboardDesignExperimentManager()
        testee = OnboardingExperimentFireAnimationHelper(fakeOnboardingDesignExperimentManager)
    }

    @Test
    fun `when non hero fire animation selected then return its res id`() {
        val selectedAnimation = FireAnimation.HeroAbstract
        val result = testee.getSelectedFireAnimationResId(selectedAnimation)
        assertEquals(R.raw.hero_abstract_airstream, result)
    }

    @Test
    fun `when hero fire animation selected and no experiments enabled return stock fire animation`() {
        val selectedAnimation = FireAnimation.HeroFire

        val result = testee.getSelectedFireAnimationResId(selectedAnimation)

        assertEquals(R.raw.hero_fire_inferno, result)
    }

    @Test
    fun `when hero fire animation selected and buck enabled then return buck experiment fire animation`() {
        fakeOnboardingDesignExperimentManager.buckEnabled = true
        val selectedAnimation = FireAnimation.HeroFire

        val result = testee.getSelectedFireAnimationResId(selectedAnimation)

        assertEquals(R.raw.buck_experiment_fire, result)
    }

    @Test
    fun `when hero fire animation selected and bb enabled then return bb experiment fire animation`() {
        fakeOnboardingDesignExperimentManager.bbEnabled = true
        val selectedAnimation = FireAnimation.HeroFire

        val result = testee.getSelectedFireAnimationResId(selectedAnimation)

        assertEquals(R.raw.bb_experiment_fire_optimised, result)
    }

    @Test
    fun `when hero fire animation selected and modified control enabled then return stock fire animation`() {
        fakeOnboardingDesignExperimentManager.modifiedControlEnabled = true
        val selectedAnimation = FireAnimation.HeroFire

        val result = testee.getSelectedFireAnimationResId(selectedAnimation)

        assertEquals(R.raw.hero_fire_inferno, result)
    }

    private class FakeOnboardDesignExperimentManager() : OnboardingDesignExperimentManager {

        var bbEnabled = false
        var buckEnabled = false
        var modifiedControlEnabled = false

        override fun getCohort(): String? {
            TODO("Not yet implemented")
        }

        override suspend fun enroll() {
            TODO("Not yet implemented")
        }

        override fun isAnyExperimentEnrolledAndEnabled(): Boolean {
            TODO("Not yet implemented")
        }

        override fun isModifiedControlEnrolledAndEnabled(): Boolean = modifiedControlEnabled

        override fun isBuckEnrolledAndEnabled(): Boolean = buckEnabled

        override fun isBbEnrolledAndEnabled(): Boolean = bbEnabled

        override suspend fun fireIntroScreenDisplayedPixel() {
            TODO("Not yet implemented")
        }

        override suspend fun fireComparisonScreenDisplayedPixel() {
            TODO("Not yet implemented")
        }

        override suspend fun fireChooseBrowserPixel() {
            TODO("Not yet implemented")
        }

        override suspend fun fireSetDefaultRatePixel() {
            TODO("Not yet implemented")
        }

        override suspend fun fireSetAddressBarDisplayedPixel() {
            TODO("Not yet implemented")
        }

        override suspend fun fireAddressBarSetTopPixel() {
            TODO("Not yet implemented")
        }

        override suspend fun fireAddressBarSetBottomPixel() {
            TODO("Not yet implemented")
        }

        override suspend fun fireSearchOrNavCustomPixel() {
            TODO("Not yet implemented")
        }

        override suspend fun firePrivacyDashClickedFromOnboardingPixel() {
            TODO("Not yet implemented")
        }

        override suspend fun fireFireButtonClickedFromOnboardingPixel() {
            TODO("Not yet implemented")
        }

        override suspend fun fireInContextDialogShownPixel(cta: Cta?) {
            TODO("Not yet implemented")
        }

        override suspend fun fireOptionSelectedPixel(
            cta: Cta,
            index: Int,
        ) {
            TODO("Not yet implemented")
        }

        override suspend fun fireSiteSuggestionOptionSelectedPixel(index: Int) {
            TODO("Not yet implemented")
        }

        override suspend fun onWebPageFinishedLoading(url: String?) {
            TODO("Not yet implemented")
        }
    }
}
