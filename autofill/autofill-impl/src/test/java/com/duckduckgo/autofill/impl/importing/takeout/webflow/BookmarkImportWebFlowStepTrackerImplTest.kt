package com.duckduckgo.autofill.impl.importing.takeout.webflow

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.impl.importing.takeout.processor.BookmarkImportProcessor
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookmarkImportWebFlowStepTrackerImplTest {

    private val testee = BookmarkImportWebFlowStepTrackerImpl()

    @Test
    fun whenCreatedThenCurrentStepIsUninitialized() {
        assertEquals("uninitialized", testee.getCurrentStep())
    }

    @Test
    fun whenUpdateStepFromAccountsUrlFromUninitializedThenSetsToLoginFirst() {
        testee.updateStepFromUrl("https://accounts.google.com/signin")
        assertEquals("login-first", testee.getCurrentStep())
    }

    @Test
    fun whenUpdateStepFromAccountsUrlFromLoginFirstThenStaysTheSame() {
        testee.updateStepFromUrl("https://accounts.google.com/signin")
        testee.updateStepFromUrl("https://accounts.google.com/signin")
        assertEquals("login-first", testee.getCurrentStep())
    }

    @Test
    fun whenUpdateStepFromAccountsUrlFromTakeoutFirstThenSetsToLoginFirst() {
        testee.updateStepFromUrl("https://takeout.google.com")
        testee.updateStepFromUrl("https://accounts.google.com/signin")
        assertEquals("login-first", testee.getCurrentStep())
    }

    @Test
    fun whenVisitingLoginMultipleTimesAfterTakeoutThenStaysAtLoginFirst() {
        testee.updateStepFromUrl("https://takeout.google.com")
        testee.updateStepFromUrl("https://accounts.google.com/signin")
        testee.updateStepFromUrl("https://accounts.google.com/signin")
        assertEquals("login-first", testee.getCurrentStep())
    }

    @Test
    fun whenReturnToLoginAfterVisitingElsewhereThenSetsToLoginRepeated() {
        testee.updateStepFromUrl("https://accounts.google.com/signin") // First login visit
        testee.updateStepFromUrl("https://takeout.google.com") // Go to takeout
        testee.updateStepFromUrl("https://accounts.google.com/signin") // Return to login
        assertEquals("login-repeat", testee.getCurrentStep())
    }

    @Test
    fun whenUpdateStepFromTakeoutUrlFromUninitializedThenSetsToTakeoutFirst() {
        testee.updateStepFromUrl("https://takeout.google.com")
        assertEquals("takeout-first", testee.getCurrentStep())
    }

    @Test
    fun whenUpdateStepFromTakeoutUrlFromTakeoutFirstThenStaysTheSame() {
        testee.updateStepFromUrl("https://takeout.google.com")
        testee.updateStepFromUrl("https://takeout.google.com")
        assertEquals("takeout-first", testee.getCurrentStep())
    }

    @Test
    fun whenUpdateStepFromTakeoutUrlFromLoginFirstThenSetsToTakeoutFirst() {
        testee.updateStepFromUrl("https://accounts.google.com/signin")
        testee.updateStepFromUrl("https://takeout.google.com")
        assertEquals("takeout-first", testee.getCurrentStep())
    }

    @Test
    fun whenUpdateStepFromTakeoutUrlAfterFirstVisitThenSetsToTakeoutRepeated() {
        testee.updateStepFromUrl("https://takeout.google.com") // First visit
        testee.updateStepFromUrl("https://accounts.google.com/signin") // Go to login
        testee.updateStepFromUrl("https://takeout.google.com") // Back to takeout
        assertEquals("takeout-repeat", testee.getCurrentStep())
    }

    @Test
    fun whenUpdateStepFromNullUrlThenStepRemainsUnchanged() {
        val initialStep = testee.getCurrentStep()
        testee.updateStepFromUrl(null)
        assertEquals(initialStep, testee.getCurrentStep())
    }

    @Test
    fun whenUpdateStepFromMalformedUrlThenStepRemainsUnchanged() {
        val initialStep = testee.getCurrentStep()
        testee.updateStepFromUrl("not-a-valid-url")
        assertEquals(initialStep, testee.getCurrentStep())
    }

    @Test
    fun whenUpdateStepFromOtherUrlThenSetsToUnknownUrl() {
        testee.updateStepFromUrl("https://example.com")
        assertEquals("unknown-url", testee.getCurrentStep())
    }

    @Test
    fun whenUpdateStepFromUppercaseTakeoutUrlThenSetsToTakeoutFirst() {
        testee.updateStepFromUrl("https://TAKEOUT.GOOGLE.COM")
        assertEquals("takeout-first", testee.getCurrentStep())
    }

    @Test
    fun whenUpdateStepFromUppercaseAccountsUrlThenSetsToLoginFirst() {
        testee.updateStepFromUrl("https://ACCOUNTS.GOOGLE.COM/signin")
        assertEquals("login-first", testee.getCurrentStep())
    }

    @Test
    fun whenUpdateStepToDownloadDetectedThenSetsCorrectStep() {
        testee.updateStepToDownloadDetected()
        assertEquals("download-detected", testee.getCurrentStep())
    }

    @Test
    fun whenUpdateStepFromImportResultSuccessThenSetsCorrectStep() {
        testee.updateStepFromImportResult(BookmarkImportProcessor.ImportResult.Success(10))
        assertEquals("completed-successful", testee.getCurrentStep())
    }

    @Test
    fun whenUpdateStepFromImportResultDownloadErrorThenSetsCorrectStep() {
        testee.updateStepFromImportResult(BookmarkImportProcessor.ImportResult.Error.DownloadError)
        assertEquals("completed-failure-download", testee.getCurrentStep())
    }

    @Test
    fun whenUpdateStepFromImportResultParseErrorThenSetsCorrectStep() {
        testee.updateStepFromImportResult(BookmarkImportProcessor.ImportResult.Error.ParseError)
        assertEquals("completed-failure-parse", testee.getCurrentStep())
    }

    @Test
    fun whenUpdateStepFromImportResultImportErrorThenSetsCorrectStep() {
        testee.updateStepFromImportResult(BookmarkImportProcessor.ImportResult.Error.ImportError)
        assertEquals("completed-failure-import", testee.getCurrentStep())
    }

    @Test
    fun whenUpdateLatestStepSpecificStageThenSetsToActionID() {
        testee.updateLatestStepSpecificStage("custom-action-id")
        assertEquals("custom-action-id", testee.getCurrentStep())
    }

    @Test
    fun whenAccountsUrlContainsTakeoutInPathThenStillDetectedAsAccounts() {
        testee.updateStepFromUrl("https://accounts.google.com/signin?continue=https://takeout.google.com")
        assertEquals("login-first", testee.getCurrentStep())
    }

    @Test
    fun whenStartFlowThenResetsToUninitialized() {
        testee.updateStepFromUrl("https://takeout.google.com")
        testee.updateStepFromUrl("https://accounts.google.com/signin")

        testee.startFlow()

        assertEquals("uninitialized", testee.getCurrentStep())
    }

    @Test
    fun whenStartFlowThenResetsFlags() {
        testee.startFlow()

        // First visits should be "first", not "repeat"
        testee.updateStepFromUrl("https://takeout.google.com")
        assertEquals("takeout-first", testee.getCurrentStep())

        testee.updateStepFromUrl("https://accounts.google.com/signin")
        assertEquals("login-first", testee.getCurrentStep())
    }
}
