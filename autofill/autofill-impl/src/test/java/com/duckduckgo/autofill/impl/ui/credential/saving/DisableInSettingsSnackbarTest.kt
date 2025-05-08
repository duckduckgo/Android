package com.duckduckgo.autofill.impl.ui.credential.saving

import android.content.Context
import android.view.View
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.api.TestActivity
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.navigation.api.GlobalActivityStarter
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class DisableInSettingsSnackbarTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(TestActivity::class.java)

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val pixel: Pixel = mock()
    private val globalActivityStarter: GlobalActivityStarter = mock()

    private lateinit var context: Context
    private lateinit var view: View
    private lateinit var testee: DisableInSettingsSnackbar
    private val autofillFeature = FakeFeatureToggleFactory.create(AutofillFeature::class.java)

    @Before
    fun setUp() {
        activityRule.scenario.onActivity { activity ->
            context = activity
            view = activity.findViewById(android.R.id.content)
            testee = DisableInSettingsSnackbar(
                pixel = pixel,
                globalActivityStarter = globalActivityStarter,
                context = context,
                view = view,
                autofillFeature = autofillFeature,
            )
        }
    }

    @Test
    fun whenShowPromptThenFireShownPixel() {
        testee.showPrompt()
        verify(pixel).fire(AutofillPixelNames.AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_SNACKBAR_SHOWN)
    }
}
