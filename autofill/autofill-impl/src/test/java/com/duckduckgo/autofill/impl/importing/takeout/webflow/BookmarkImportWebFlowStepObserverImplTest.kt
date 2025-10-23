package com.duckduckgo.autofill.impl.importing.takeout.webflow

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.impl.importing.takeout.processor.BookmarkImportProcessor
import com.duckduckgo.autofill.impl.importing.takeout.webflow.BookmarkImportWebFlowStepObserver.Step
import com.duckduckgo.autofill.impl.importing.takeout.webflow.BookmarkImportWebFlowStepObserver.Step.DownloadDetected
import com.duckduckgo.autofill.impl.importing.takeout.webflow.BookmarkImportWebFlowStepObserver.Step.ImportFinished
import com.duckduckgo.autofill.impl.importing.takeout.webflow.BookmarkImportWebFlowStepObserver.Step.UrlVisited
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookmarkImportWebFlowStepObserverImplTest {

    private val testee = BookmarkImportWebFlowStepObserverImpl()

    @Test
    fun whenCreatedThenCurrentStepIsUninitialized() {
        assertEquals("uninitialized", testee.getCurrentStep())
    }

    @Test
    fun whenUpdateStepFromAccountsUrlFromUninitializedThenSetsToLoginFirst() {
        testee.updateStep(UrlVisited("https://accounts.google.com/signin"))
        assertEquals("login-first", testee.getCurrentStep())
    }

    @Test
    fun whenUpdateStepFromAccountsUrlFromLoginFirstThenStaysTheSame() {
        testee.updateStep(UrlVisited("https://accounts.google.com/signin"))
        testee.updateStep(UrlVisited("https://accounts.google.com/signin"))
        assertEquals("login-first", testee.getCurrentStep())
    }

    @Test
    fun whenUpdateStepFromAccountsUrlFromTakeoutFirstThenSetsToLoginFirst() {
        testee.updateStep(UrlVisited("https://takeout.google.com"))
        testee.updateStep(UrlVisited("https://accounts.google.com/signin"))
        assertEquals("login-first", testee.getCurrentStep())
    }

    @Test
    fun whenVisitingLoginMultipleTimesAfterTakeoutThenStaysAtLoginFirst() {
        testee.updateStep(UrlVisited("https://takeout.google.com"))
        testee.updateStep(UrlVisited("https://accounts.google.com/signin"))
        testee.updateStep(UrlVisited("https://accounts.google.com/signin"))
        assertEquals("login-first", testee.getCurrentStep())
    }

    @Test
    fun whenReturnToLoginAfterVisitingElsewhereThenSetsToLoginRepeated() {
        testee.updateStep(UrlVisited("https://accounts.google.com/signin")) // First login visit
        testee.updateStep(UrlVisited("https://takeout.google.com")) // Go to takeout
        testee.updateStep(UrlVisited("https://accounts.google.com/signin")) // Return to login
        assertEquals("login-repeat", testee.getCurrentStep())
    }

    @Test
    fun whenUpdateStepFromTakeoutUrlFromUninitializedThenSetsToTakeoutFirst() {
        testee.updateStep(UrlVisited("https://takeout.google.com"))
        assertEquals("takeout-first", testee.getCurrentStep())
    }

    @Test
    fun whenUpdateStepFromTakeoutUrlFromTakeoutFirstThenStaysTheSame() {
        testee.updateStep(UrlVisited("https://takeout.google.com"))
        testee.updateStep(UrlVisited("https://takeout.google.com"))
        assertEquals("takeout-first", testee.getCurrentStep())
    }

    @Test
    fun whenUpdateStepFromTakeoutUrlFromLoginFirstThenSetsToTakeoutFirst() {
        testee.updateStep(UrlVisited("https://accounts.google.com/signin"))
        testee.updateStep(UrlVisited("https://takeout.google.com"))
        assertEquals("takeout-first", testee.getCurrentStep())
    }

    @Test
    fun whenUpdateStepFromTakeoutUrlAfterFirstVisitThenSetsToTakeoutRepeated() {
        testee.updateStep(UrlVisited("https://takeout.google.com")) // First visit
        testee.updateStep(UrlVisited("https://accounts.google.com/signin")) // Go to login
        testee.updateStep(UrlVisited("https://takeout.google.com")) // Back to takeout
        assertEquals("takeout-repeat", testee.getCurrentStep())
    }

    @Test
    fun whenUpdateStepFromNullUrlThenStepRemainsUnchanged() {
        val initialStep = testee.getCurrentStep()
        testee.updateStep(UrlVisited(null))
        assertEquals(initialStep, testee.getCurrentStep())
    }

    @Test
    fun whenUpdateStepFromMalformedUrlThenStepRemainsUnchanged() {
        val initialStep = testee.getCurrentStep()
        testee.updateStep(UrlVisited("not-a-valid-url"))
        assertEquals(initialStep, testee.getCurrentStep())
    }

    @Test
    fun whenUpdateStepFromOtherUrlThenSetsToUnknownUrl() {
        testee.updateStep(UrlVisited("https://example.com"))
        assertEquals("unknown-url", testee.getCurrentStep())
    }

    @Test
    fun whenUpdateStepFromUppercaseTakeoutUrlThenSetsToTakeoutFirst() {
        testee.updateStep(UrlVisited("https://TAKEOUT.GOOGLE.COM"))
        assertEquals("takeout-first", testee.getCurrentStep())
    }

    @Test
    fun whenUpdateStepFromUppercaseAccountsUrlThenSetsToLoginFirst() {
        testee.updateStep(UrlVisited("https://ACCOUNTS.GOOGLE.COM/signin"))
        assertEquals("login-first", testee.getCurrentStep())
    }

    @Test
    fun whenUpdateStepToDownloadDetectedThenSetsCorrectStep() {
        testee.updateStep(DownloadDetected)
        assertEquals("download-detected", testee.getCurrentStep())
    }

    @Test
    fun whenUpdateStepFromImportResultSuccessThenSetsCorrectStep() {
        testee.updateStep(ImportFinished(BookmarkImportProcessor.ImportResult.Success(10)))
        assertEquals("completed-successful", testee.getCurrentStep())
    }

    @Test
    fun whenUpdateStepFromImportResultDownloadErrorThenSetsCorrectStep() {
        testee.updateStep(ImportFinished(BookmarkImportProcessor.ImportResult.Error.DownloadError))
        assertEquals("completed-failure-download", testee.getCurrentStep())
    }

    @Test
    fun whenUpdateStepFromImportResultParseErrorThenSetsCorrectStep() {
        testee.updateStep(ImportFinished(BookmarkImportProcessor.ImportResult.Error.ParseError))
        assertEquals("completed-failure-parse", testee.getCurrentStep())
    }

    @Test
    fun whenUpdateStepFromImportResultImportErrorThenSetsCorrectStep() {
        testee.updateStep(ImportFinished(BookmarkImportProcessor.ImportResult.Error.ImportError))
        assertEquals("completed-failure-import", testee.getCurrentStep())
    }

    @Test
    fun whenUpdateLatestStepSpecificStageThenSetsToActionID() {
        testee.updateStep(Step.JavascriptStep("custom-action-id"))
        assertEquals("custom-action-id", testee.getCurrentStep())
    }

    @Test
    fun whenAccountsUrlContainsTakeoutInPathThenStillDetectedAsAccounts() {
        testee.updateStep(UrlVisited("https://accounts.google.com/signin?continue=https://takeout.google.com"))
        assertEquals("login-first", testee.getCurrentStep())
    }

    @Test
    fun whenStartFlowThenResetsToUninitialized() {
        testee.updateStep(UrlVisited("https://takeout.google.com"))
        testee.updateStep(UrlVisited("https://accounts.google.com/signin"))

        testee.startFlow()

        assertEquals("uninitialized", testee.getCurrentStep())
    }

    @Test
    fun whenStartFlowThenResetsFlags() {
        testee.startFlow()

        // First visits should be "first", not "repeat"
        testee.updateStep(UrlVisited("https://takeout.google.com"))
        assertEquals("takeout-first", testee.getCurrentStep())

        testee.updateStep(UrlVisited("https://accounts.google.com/signin"))
        assertEquals("login-first", testee.getCurrentStep())
    }
}
