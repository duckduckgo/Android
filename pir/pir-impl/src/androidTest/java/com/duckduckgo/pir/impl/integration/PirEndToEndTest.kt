/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.pir.impl.integration

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.common.utils.DefaultDispatcherProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.pir.impl.callbacks.PirCallbacks
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStepActions
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStepActions.OptOutStepActions
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStepActions.ScanStepActions
import com.duckduckgo.pir.impl.common.PirRunStateHandler
import com.duckduckgo.pir.impl.common.RealBrokerStepsParser
import com.duckduckgo.pir.impl.common.RealPirRunStateHandler
import com.duckduckgo.pir.impl.common.actions.BrokerActionFailedEventHandler
import com.duckduckgo.pir.impl.common.actions.BrokerStepCompletedEventHandler
import com.duckduckgo.pir.impl.common.actions.CaptchaInfoReceivedEventHandler
import com.duckduckgo.pir.impl.common.actions.ConditionExpectationSucceededEventHandler
import com.duckduckgo.pir.impl.common.actions.EmailReceivedEventHandler
import com.duckduckgo.pir.impl.common.actions.ErrorReceivedHandler
import com.duckduckgo.pir.impl.common.actions.EventHandler
import com.duckduckgo.pir.impl.common.actions.ExecuteBrokerStepActionEventHandler
import com.duckduckgo.pir.impl.common.actions.ExecuteNextBrokerStepEventHandler
import com.duckduckgo.pir.impl.common.actions.JsActionSuccessEventHandler
import com.duckduckgo.pir.impl.common.actions.LoadUrlCompleteEventHandler
import com.duckduckgo.pir.impl.common.actions.LoadUrlFailedEventHandler
import com.duckduckgo.pir.impl.common.actions.RealPirActionsRunnerStateEngineFactory
import com.duckduckgo.pir.impl.common.actions.RetryAwaitCaptchaSolutionEventHandler
import com.duckduckgo.pir.impl.common.actions.RetryGetCaptchaSolutionEventHandler
import com.duckduckgo.pir.impl.common.actions.StartedEventHandler
import com.duckduckgo.pir.impl.email.PirEmailConfirmation
import com.duckduckgo.pir.impl.email.PirEmailConfirmationJobsRunner
import com.duckduckgo.pir.impl.email.RealPirEmailConfirmation
import com.duckduckgo.pir.impl.email.RealPirEmailConfirmationJobsRunner
import com.duckduckgo.pir.impl.integration.fakes.FakeCurrentTimeProvider
import com.duckduckgo.pir.impl.integration.fakes.FakeDbpService
import com.duckduckgo.pir.impl.integration.fakes.FakeEventHandlerPluginPoint
import com.duckduckgo.pir.impl.integration.fakes.FakeNativeBrokerActionHandler
import com.duckduckgo.pir.impl.integration.fakes.FakeNetworkProtectionState
import com.duckduckgo.pir.impl.integration.fakes.FakePirCssScriptLoader
import com.duckduckgo.pir.impl.integration.fakes.FakePirDataStore
import com.duckduckgo.pir.impl.integration.fakes.FakePirDetachedWebViewProvider
import com.duckduckgo.pir.impl.integration.fakes.FakePirMessagingInterface
import com.duckduckgo.pir.impl.integration.fakes.FakePixel
import com.duckduckgo.pir.impl.integration.fakes.FakePluginPoint
import com.duckduckgo.pir.impl.integration.fakes.FakeWebViewDataCleaner
import com.duckduckgo.pir.impl.integration.fakes.TestPirActionsRunnerFactory
import com.duckduckgo.pir.impl.integration.fakes.TestPirSecureStorageDatabaseFactory
import com.duckduckgo.pir.impl.models.Address
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord.OptOutJobStatus
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.ScanJobRecord.ScanJobStatus
import com.duckduckgo.pir.impl.optout.RealPirOptOut
import com.duckduckgo.pir.impl.pixels.PirPixel
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.pixels.RealPirPixelSender
import com.duckduckgo.pir.impl.scan.RealPirScan
import com.duckduckgo.pir.impl.scheduling.JobRecordUpdater
import com.duckduckgo.pir.impl.scheduling.PirExecutionType
import com.duckduckgo.pir.impl.scheduling.RealEligibleOptOutJobProvider
import com.duckduckgo.pir.impl.scheduling.RealEligibleScanJobProvider
import com.duckduckgo.pir.impl.scheduling.RealJobRecordUpdater
import com.duckduckgo.pir.impl.scheduling.RealPirJobsRunner
import com.duckduckgo.pir.impl.scripts.RealBrokerActionProcessor
import com.duckduckgo.pir.impl.scripts.models.BrokerAction
import com.duckduckgo.pir.impl.scripts.models.PirScriptRequestData
import com.duckduckgo.pir.impl.scripts.models.PirScriptRequestData.SolveCaptcha
import com.duckduckgo.pir.impl.scripts.models.PirScriptRequestData.UserProfile
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.ClickResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.ConditionResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.ExpectationResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.ExtractedResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.FillFormResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.GetCaptchaInfoResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.NavigateResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.SolveCaptchaResponse
import com.duckduckgo.pir.impl.store.PirEventsRepository
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.store.PirSchedulingRepository
import com.duckduckgo.pir.impl.store.RealPirEventsRepository
import com.duckduckgo.pir.impl.store.RealPirRepository
import com.duckduckgo.pir.impl.store.RealPirSchedulingRepository
import com.duckduckgo.pir.impl.store.db.BrokerEntity
import com.duckduckgo.pir.impl.store.db.BrokerOptOut
import com.duckduckgo.pir.impl.store.db.BrokerScan
import com.duckduckgo.pir.impl.store.db.BrokerSchedulingConfigEntity
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end tests for PIR flow using (mostly) real implementations
 * with faked boundaries (messaging, webview, network).
 *
 * The test covers:
 * 1. Saving a profile and loading brokers
 * 2. Run Eligible Scan and Opt-Out Jobs
 * 3. Verify Extracted Profiles
 * 4. Verify Opt-Out Jobs Created
 * 5. Verify Opt-Out Jobs Executed (Pending Email Confirmation)
 * 6. Verify all scan and opt-out pixels fired
 * 7. Email Confirmation
 * 8. Confirmation/Maintenance scan
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class PirEndToEndTest {

    private lateinit var context: Context
    private lateinit var moshi: Moshi
    private lateinit var pirRepository: PirRepository
    private lateinit var pirSchedulingRepository: PirSchedulingRepository
    private lateinit var pirEventsRepository: PirEventsRepository
    private lateinit var pixelSender: PirPixelSender
    private lateinit var pirRunStateHandler: PirRunStateHandler
    private lateinit var jobRecordUpdater: JobRecordUpdater
    private lateinit var brokerActionProcessor: RealBrokerActionProcessor
    private lateinit var brokerStepsParser: RealBrokerStepsParser
    private lateinit var engineFactory: RealPirActionsRunnerStateEngineFactory
    private lateinit var pirScan: RealPirScan
    private lateinit var pirOptOut: RealPirOptOut
    private lateinit var pirEmailConfirmation: PirEmailConfirmation
    private lateinit var eligibleScanJobProvider: RealEligibleScanJobProvider
    private lateinit var pirJobsRunner: RealPirJobsRunner
    private lateinit var eligibleOptOutJobProvider: RealEligibleOptOutJobProvider
    private lateinit var pirEmailConfirmationJobsRunner: PirEmailConfirmationJobsRunner

    private lateinit var dispatcherProvider: DispatcherProvider
    private lateinit var testScope: CoroutineScope
    private lateinit var databaseFactory: TestPirSecureStorageDatabaseFactory
    private lateinit var pirActionsRunnerFactory: TestPirActionsRunnerFactory
    private lateinit var fakePirDataStore: FakePirDataStore
    private lateinit var fakeDbpService: FakeDbpService
    private lateinit var fakeTimeProvider: FakeCurrentTimeProvider
    private lateinit var fakePixel: FakePixel
    private lateinit var fakePirMessagingInterface: FakePirMessagingInterface
    private lateinit var fakePirDetachedWebViewProvider: FakePirDetachedWebViewProvider
    private lateinit var fakeNativeBrokerActionHandler: FakeNativeBrokerActionHandler
    private lateinit var fakePirCssScriptLoader: FakePirCssScriptLoader
    private lateinit var fakePirWebViewDataCleaner: FakeWebViewDataCleaner
    private lateinit var fakeNetworkProtectionState: FakeNetworkProtectionState

    private val activeBrokerName = "FakeBroker"
    private val removedBrokerName = "FakeRemovedBroker"
    private val unknownScanActionBrokerName = "FakeBrokerScanUnknownAction"
    private val unknownOptOutActionBrokerName = "FakeBrokerOptOutUnknownAction"
    private val testProfile = ProfileQuery(
        id = 1L,
        firstName = "John",
        lastName = "Doe",
        city = "New York",
        state = "NY",
        addresses = listOf(
            Address(
                city = "New York",
                state = "NY",
            ),
        ),
        birthYear = 1990,
        fullName = "John Doe",
        age = 34,
        deprecated = false,
    )

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext

        // Initialize Moshi with polymorphic adapters (same as PirModule)
        moshi = Moshi.Builder()
            .add(
                PolymorphicJsonAdapterFactory.of(PirScriptRequestData::class.java, "data")
                    .withSubtype(SolveCaptcha::class.java, "solveCaptcha")
                    .withSubtype(UserProfile::class.java, "userProfile"),
            ).add(
                PolymorphicJsonAdapterFactory.of(BrokerAction::class.java, "actionType")
                    .withSubtype(BrokerAction.Extract::class.java, "extract")
                    .withSubtype(BrokerAction.Expectation::class.java, "expectation")
                    .withSubtype(BrokerAction.Click::class.java, "click")
                    .withSubtype(BrokerAction.FillForm::class.java, "fillForm")
                    .withSubtype(BrokerAction.Navigate::class.java, "navigate")
                    .withSubtype(BrokerAction.GetCaptchaInfo::class.java, "getCaptchaInfo")
                    .withSubtype(BrokerAction.SolveCaptcha::class.java, "solveCaptcha")
                    .withSubtype(BrokerAction.EmailConfirmation::class.java, "emailConfirmation")
                    .withSubtype(BrokerAction.Condition::class.java, "condition"),
            ).add(
                PolymorphicJsonAdapterFactory.of(BrokerStepActions::class.java, "stepType")
                    .withSubtype(ScanStepActions::class.java, "scan")
                    .withSubtype(OptOutStepActions::class.java, "optOut"),
            ).add(
                PolymorphicJsonAdapterFactory.of(PirSuccessResponse::class.java, "actionType")
                    .withSubtype(NavigateResponse::class.java, "navigate")
                    .withSubtype(ExtractedResponse::class.java, "extract")
                    .withSubtype(GetCaptchaInfoResponse::class.java, "getCaptchaInfo")
                    .withSubtype(SolveCaptchaResponse::class.java, "solveCaptcha")
                    .withSubtype(ClickResponse::class.java, "click")
                    .withSubtype(ExpectationResponse::class.java, "expectation")
                    .withSubtype(FillFormResponse::class.java, "fillForm")
                    .withSubtype(ConditionResponse::class.java, "condition"),
            )
            .add(KotlinJsonAdapterFactory())
            .build()

        fakeTimeProvider = FakeCurrentTimeProvider(initialTimeMs = 1700000000000L)
        dispatcherProvider = DefaultDispatcherProvider()
        @Suppress("NoHardcodedCoroutineDispatcher")
        testScope = CoroutineScope(Dispatchers.Default)

        databaseFactory = TestPirSecureStorageDatabaseFactory(context)
        fakePirDataStore = FakePirDataStore()
        fakeDbpService = FakeDbpService()

        fakePixel = FakePixel()
        pixelSender = RealPirPixelSender(fakePixel)
        fakePirWebViewDataCleaner = FakeWebViewDataCleaner()
        fakeNetworkProtectionState = FakeNetworkProtectionState()

        pirRepository = RealPirRepository(
            dispatcherProvider = dispatcherProvider,
            pirDataStore = fakePirDataStore,
            currentTimeProvider = fakeTimeProvider,
            databaseFactory = databaseFactory,
            dbpService = fakeDbpService,
            pixelSender = pixelSender,
            appCoroutineScope = testScope,
        )

        pirSchedulingRepository = RealPirSchedulingRepository(
            dispatcherProvider = dispatcherProvider,
            currentTimeProvider = fakeTimeProvider,
            databaseFactory = databaseFactory,
            appCoroutineScope = testScope,
        )

        pirEventsRepository = RealPirEventsRepository(
            moshi = moshi,
            dispatcherProvider = dispatcherProvider,
            databaseFactory = databaseFactory,
            appCoroutineScope = testScope,
        )

        fakePirMessagingInterface = FakePirMessagingInterface(moshi)
        fakePirDetachedWebViewProvider = FakePirDetachedWebViewProvider()
        fakeNativeBrokerActionHandler = FakeNativeBrokerActionHandler()
        fakePirCssScriptLoader = FakePirCssScriptLoader()

        jobRecordUpdater = RealJobRecordUpdater(
            dispatcherProvider = dispatcherProvider,
            currentTimeProvider = fakeTimeProvider,
            schedulingRepository = pirSchedulingRepository,
            repository = pirRepository,
        )

        pirRunStateHandler = RealPirRunStateHandler(
            repository = pirRepository,
            eventsRepository = pirEventsRepository,
            pixelSender = pixelSender,
            dispatcherProvider = dispatcherProvider,
            jobRecordUpdater = jobRecordUpdater,
            pirSchedulingRepository = pirSchedulingRepository,
            currentTimeProvider = fakeTimeProvider,
            moshi = moshi,
            networkProtectionState = fakeNetworkProtectionState,
        )

        brokerActionProcessor = RealBrokerActionProcessor(fakePirMessagingInterface, moshi)
        brokerStepsParser = RealBrokerStepsParser(dispatcherProvider, pirRepository, moshi)

        val eventHandlers = createEventHandlers()
        val eventHandlerPluginPoint = FakeEventHandlerPluginPoint(eventHandlers)

        engineFactory = RealPirActionsRunnerStateEngineFactory(
            eventHandlerPluginPoint,
            dispatcherProvider,
            testScope,
        )

        pirActionsRunnerFactory = TestPirActionsRunnerFactory(
            dispatcherProvider = dispatcherProvider,
            pirDetachedWebViewProvider = fakePirDetachedWebViewProvider,
            brokerActionProcessor = brokerActionProcessor,
            nativeBrokerActionHandler = fakeNativeBrokerActionHandler,
            engineFactory = engineFactory,
            coroutineScope = testScope,
        )

        val pirCallbacksPluginPoint = FakePluginPoint<PirCallbacks>()

        pirScan = RealPirScan(
            repository = pirRepository,
            eventsRepository = pirEventsRepository,
            brokerStepsParser = brokerStepsParser,
            pirCssScriptLoader = fakePirCssScriptLoader,
            pirActionsRunnerFactory = pirActionsRunnerFactory,
            currentTimeProvider = fakeTimeProvider,
            dispatcherProvider = dispatcherProvider,
            callbacks = pirCallbacksPluginPoint,
            webViewDataCleaner = fakePirWebViewDataCleaner,
        )

        pirOptOut = RealPirOptOut(
            repository = pirRepository,
            eventsRepository = pirEventsRepository,
            brokerStepsParser = brokerStepsParser,
            pirCssScriptLoader = fakePirCssScriptLoader,
            pirActionsRunnerFactory = pirActionsRunnerFactory,
            currentTimeProvider = fakeTimeProvider,
            dispatcherProvider = dispatcherProvider,
            callbacks = pirCallbacksPluginPoint,
            webViewDataCleaner = fakePirWebViewDataCleaner,
        )

        eligibleScanJobProvider = RealEligibleScanJobProvider(
            dispatcherProvider = dispatcherProvider,
            pirSchedulingRepository = pirSchedulingRepository,
            pirRepository = pirRepository,
        )
        eligibleOptOutJobProvider = RealEligibleOptOutJobProvider(
            dispatcherProvider = dispatcherProvider,
            pirSchedulingRepository = pirSchedulingRepository,
            pirRepository = pirRepository,
        )

        pirJobsRunner = RealPirJobsRunner(
            dispatcherProvider = dispatcherProvider,
            pirRepository = pirRepository,
            pirSchedulingRepository = pirSchedulingRepository,
            eligibleOptOutJobProvider = eligibleOptOutJobProvider,
            eligibleScanJobProvider = eligibleScanJobProvider,
            pirScan = pirScan,
            pirOptOut = pirOptOut,
            currentTimeProvider = fakeTimeProvider,
            pixelSender = pixelSender,
        )

        pirEmailConfirmation = RealPirEmailConfirmation(
            repository = pirRepository,
            brokerStepsParser = brokerStepsParser,
            pirCssScriptLoader = fakePirCssScriptLoader,
            pirActionsRunnerFactory = pirActionsRunnerFactory,
            dispatcherProvider = dispatcherProvider,
            callbacks = pirCallbacksPluginPoint,
            webViewDataCleaner = fakePirWebViewDataCleaner,
        )

        pirEmailConfirmationJobsRunner = RealPirEmailConfirmationJobsRunner(
            dispatcherProvider = dispatcherProvider,
            pirSchedulingRepository = pirSchedulingRepository,
            pirRepository = pirRepository,
            jobRecordUpdater = jobRecordUpdater,
            emailConfirmation = pirEmailConfirmation,
            pirPixelSender = pixelSender,
            currentTimeProvider = fakeTimeProvider,
            pirEventsRepository = pirEventsRepository,
        )
    }

    @After
    fun tearDown() {
        databaseFactory.closeDatabase()
    }

    @Test
    fun testPirEndToEndBehavesCorrectly() = runBlocking {
        println("==================== STEP 1: Save Profile ====================")

        // Load brokers into repository
        loadBrokers()

        // Save test profile
        pirRepository.replaceUserProfile(testProfile)

        // Verify: Profile is saved
        val savedProfiles = pirRepository.getAllUserProfileQueries()
        assertEquals("Should have 1 profile", 1, savedProfiles.size)
        assertEquals("Profile firstName should match", "John", savedProfiles[0].firstName)

        // Verify: Both brokers loaded
        val db = databaseFactory.getDatabaseSync()
        val allBrokersInDb = db.brokerDao().getAllBrokersNamesWithScanSteps()
        assertEquals("Should have 2 brokers loaded", 2, allBrokersInDb.size)

        // Verify: Only non-removed brokers are "active"
        val activeBrokers = pirRepository.getAllActiveBrokers()
        assertEquals("Should have 1 active broker", 1, activeBrokers.size)
        assertEquals("Active broker should be FakeBroker", activeBrokerName, activeBrokers[0])

        // Verify: Removed broker exists but is filtered from active brokers
        assertFalse("Removed broker should not be in active brokers", activeBrokers.contains(removedBrokerName))

        println("==================== STEP 2: Run Eligible Scan and Opt-Out Jobs ====================")

        // Run eligible jobs - this will trigger PirScan and PirOptOut
        val scanResult = pirJobsRunner.runEligibleJobs(context, PirExecutionType.MANUAL)
        assertTrue("Scan should succeed", scanResult.isSuccess)

        // Verify: Scan jobs created only for active broker
        val scanJobRecords = pirSchedulingRepository.getAllValidScanJobRecords()
        assertEquals("Should have 1 scan job", 1, scanJobRecords.size)
        assertEquals("Scan job should be for active broker", activeBrokerName, scanJobRecords[0].brokerName)
        assertEquals("Scan job status should be MATCHES_FOUND", ScanJobStatus.MATCHES_FOUND, scanJobRecords[0].status)

        // Verify: No scan job for removed broker
        val removedBrokerScanJob = scanJobRecords.find { it.brokerName == removedBrokerName }
        assertNull("Removed broker should not have scan job", removedBrokerScanJob)

        // Verify: Correct scan and opt-out actions (without email) were pushed to FakePirMessagingInterface for active broker only
        assertCorrectActionsWerePushedToJsLayer(
            scanStepActionsFilter = { it },
            optOutStepActionsFilter = {
                // drop email confirmation actions as those are run in separate email confirmation step
                it.takeWhile { action -> action !is BrokerAction.EmailConfirmation }
            },
            additionalAssertionsOnPushedActions = { pushedActions ->
                // Verify navigation was to active broker URL only
                val navigateActions = pushedActions.filterIsInstance<BrokerAction.Navigate>()
                assertTrue("Should have navigate actions", navigateActions.isNotEmpty())
                navigateActions.forEach { nav ->
                    assertFalse(
                        "Navigate URL should not be to removed broker",
                        nav.url.contains("fake-removed-broker"),
                    )
                }
            },
        )

        println("==================== STEP 3: Verify Extracted Profiles ====================")

        // Verify that extracted profiles were saved for active broker only
        val extractedProfiles = pirRepository.getAllExtractedProfiles()
        assertTrue("Should have extracted profiles", extractedProfiles.isNotEmpty())

        // All extracted profiles should be from non-removed broker
        extractedProfiles.forEach { profile ->
            assertEquals("Extracted profile should be from active broker", activeBrokerName, profile.brokerName)
        }

        // Verify no profiles from removed broker
        val removedBrokerProfiles = extractedProfiles.filter { it.brokerName == removedBrokerName }
        assertTrue("Removed broker should not have extracted profiles", removedBrokerProfiles.isEmpty())

        println("===================== STEP 4: Verify Opt-Out Jobs Created ====================")

        // Verify that opt-out jobs were created for active broker only
        val optOutJobRecords = pirSchedulingRepository.getAllValidOptOutJobRecords()
        assertTrue("Should have opt-out jobs", optOutJobRecords.isNotEmpty())

        // All opt-out jobs should be for non-removed broker
        optOutJobRecords.forEach { job ->
            assertEquals("Opt-out job should be for active broker", activeBrokerName, job.brokerName)
        }

        // Verify no opt-out jobs for removed broker
        val removedBrokerOptOutJobs = optOutJobRecords.filter { it.brokerName == removedBrokerName }
        assertTrue("Removed broker should not have opt-out jobs", removedBrokerOptOutJobs.isEmpty())

        println("==========STEP 5: Verify Opt-Out Jobs Executed (Pending Email Confirmation) ==========")

        // Since broker has emailConfirmation action, status should be PENDING_EMAIL_CONFIRMATION
        val updatedOptOutJobs = pirSchedulingRepository.getAllValidOptOutJobRecords()
        val pendingEmailJobs = updatedOptOutJobs.filter { it.status == OptOutJobStatus.PENDING_EMAIL_CONFIRMATION }
        assertTrue("Should have PENDING_EMAIL_CONFIRMATION opt-out jobs", pendingEmailJobs.isNotEmpty())

        // Verify lastOptOutAttemptDate is set
        pendingEmailJobs.forEach { job ->
            assertTrue("lastOptOutAttemptDate should be set", job.lastOptOutAttemptDateInMillis > 0)
        }

        // Verify EmailConfirmationJobRecord was created
        val emailConfirmationJobs = pirSchedulingRepository.getEmailConfirmationJobsWithNoLink()
        assertTrue("Should have email confirmation jobs", emailConfirmationJobs.isNotEmpty())
        assertEquals("Email confirmation job should be for active broker", activeBrokerName, emailConfirmationJobs[0].brokerName)

        println("==========STEP 6: Verify all scan and opt-out pixels fired ==========")
        // Scan pixels
        assertPixelsWereFired(
            PirPixel.PIR_FOREGROUND_RUN_STARTED,
            PirPixel.PIR_SCAN_STARTED,
            PirPixel.PIR_SCAN_STAGE,
            PirPixel.PIR_SCAN_STAGE_RESULT_MATCHES,
            PirPixel.PIR_FOREGROUND_RUN_COMPLETED,
            PirPixel.PIR_INITIAL_SCAN_DURATION,
        )

        // Opt-out pixels
        assertPixelsWereFired(
            PirPixel.PIR_OPTOUT_STAGE_START,
            PirPixel.PIR_OPTOUT_STAGE_FILLFORM,
            PirPixel.PIR_OPTOUT_STAGE_CAPTCHA_PARSE,
            PirPixel.PIR_OPTOUT_STAGE_CAPTCHA_SEND,
            PirPixel.PIR_OPTOUT_STAGE_CAPTCHA_SOLVE,
            PirPixel.PIR_OPTOUT_STAGE_SUBMIT,
            PirPixel.PIR_OPTOUT_STAGE_PENDING_EMAIL_CONFIRMATION,
        )

        // Clear pixels before email confirmation
        fakePixel.clear()
        fakePirMessagingInterface.clearPushedActions()

        println("==================== STEP 7: Email Confirmation ====================")

        // Configure fake DbpService to return a ready link for email confirmation
        val emailJob = emailConfirmationJobs[0]
        fakeDbpService.setEmailConfirmationLinkReady(
            email = emailJob.emailData.email,
            attemptId = emailJob.emailData.attemptId,
            link = "https://fake-broker.com/confirm?token=abc123",
        )

        // Run email confirmation jobs
        val emailConfirmationResult = pirEmailConfirmationJobsRunner.runEligibleJobs(context)
        assertTrue("Email confirmation should succeed", emailConfirmationResult.isSuccess)

        // Verify opt-out job status is now REQUESTED
        val postEmailOptOutJobs = pirSchedulingRepository.getAllValidOptOutJobRecords()
        val requestedJobs = postEmailOptOutJobs.filter { it.status == OptOutJobStatus.REQUESTED }
        assertTrue("Should have REQUESTED opt-out jobs after email confirmation", requestedJobs.isNotEmpty())

        // Verify optOutRequestedDate is set
        requestedJobs.forEach { job ->
            assertTrue("optOutRequestedDate should be set", job.optOutRequestedDateInMillis > 0)
        }

        // Verify EmailConfirmationJobRecord is cleaned up
        val remainingEmailJobs = pirSchedulingRepository.getEmailConfirmationJobsWithNoLink()
        assertTrue("Email confirmation jobs should be cleaned up", remainingEmailJobs.isEmpty())

        // Verify correct email confirmation actions were pushed
        assertCorrectActionsWerePushedToJsLayer(
            scanStepActionsFilter = { listOf() },
            optOutStepActionsFilter = {
                // drop email confirmation actions as those are run in separate email confirmation step
                it.takeLastWhile { action -> action !is BrokerAction.EmailConfirmation }
            },
        )

        // Verify email confirmation pixels fired
        assertPixelsWereFired(
            PirPixel.PIR_EMAIL_CONFIRMATION_RUN_STARTED,
            PirPixel.PIR_EMAIL_CONFIRMATION_LINK_RECEIVED,
            PirPixel.PIR_EMAIL_CONFIRMATION_ATTEMPT_START,
            PirPixel.PIR_EMAIL_CONFIRMATION_ATTEMPT_SUCCESS,
            PirPixel.PIR_OPTOUT_SUBMIT_SUCCESS,
            PirPixel.PIR_EMAIL_CONFIRMATION_JOB_SUCCESS,
            PirPixel.PIR_EMAIL_CONFIRMATION_RUN_COMPLETED,
        )

        // Clear pixels before confirmation scan
        fakePixel.clear()

        println("==================== STEP 8: Confirmation Scan ====================")

        // Configure messaging interface to return empty results (profile removed from broker)
        fakePirMessagingInterface.setNextExtractResponseEmpty(true)
        fakePirMessagingInterface.clearPushedActions()

        // Advance time to make confirmation scan eligible
        // confirmOptOutScan is 72 hours from scheduling config
        fakeTimeProvider.advanceByHours(73)

        // Run confirmation scan
        val confirmationResult = pirJobsRunner.runEligibleJobs(context, PirExecutionType.SCHEDULED)
        assertTrue("Confirmation scan should succeed", confirmationResult.isSuccess)

        // Verify navigate actions were pushed for active broker only
        assertCorrectActionsWerePushedToJsLayer(
            scanStepActionsFilter = { it },
            optOutStepActionsFilter = { listOf() },
            additionalAssertionsOnPushedActions = { pushedActions ->
                val navigateActions = pushedActions.filterIsInstance<BrokerAction.Navigate>()
                assertTrue("Should have navigate actions", navigateActions.isNotEmpty())
                navigateActions.forEach { nav ->
                    assertFalse(
                        "Navigate URL should not be to removed broker",
                        nav.url.contains("fake-removed-broker"),
                    )
                }
            },
        )

        // Verify scan job status updated to NO_MATCH_FOUND (profile removed)
        val finalScanJobs = pirSchedulingRepository.getAllValidScanJobRecords()
        val activeBrokerScanJob = finalScanJobs.find { it.brokerName == activeBrokerName }
        assertEquals(
            "Active broker scan job status should be NO_MATCH_FOUND after confirmation",
            ScanJobStatus.NO_MATCH_FOUND,
            activeBrokerScanJob?.status,
        )

        // Verify no scan activity for removed broker - check that only active broker has scan jobs
        val allCurrentScanJobs = pirSchedulingRepository.getAllValidScanJobRecords()
        val removedBrokerScanJobs = allCurrentScanJobs.filter { it.brokerName == removedBrokerName }
        assertTrue("Removed broker should have no scan job records", removedBrokerScanJobs.isEmpty())

        // Verify confirmation scan pixels fired
        assertPixelsWereFired(
            PirPixel.PIR_SCHEDULED_RUN_STARTED,
            PirPixel.PIR_SCAN_STARTED,
            PirPixel.PIR_SCAN_STAGE,
            PirPixel.PIR_SCAN_STAGE_RESULT_MATCHES,
            PirPixel.PIR_SCHEDULED_RUN_COMPLETED,
        )
    }

    @Test
    fun testScanGracefullyHandlesUnknownActions() = runBlocking {
        println("==================== STEP 1: Setup broker with unknown scan action ====================")

        // This broker has an unknown action in the scan step
        loadBrokerWithScanUnknownAction()

        // Save test profile
        pirRepository.replaceUserProfile(testProfile)

        // Verify broker is loaded
        val activeBrokers = pirRepository.getAllActiveBrokers()
        assertEquals("Should have 1 active broker", 1, activeBrokers.size)
        assertTrue(
            "Should be the unknown scan action broker",
            activeBrokers.contains(unknownScanActionBrokerName),
        )

        println("==================== STEP 2: Run scan - should fail gracefully ====================")

        // Run eligible jobs - should not crash even though scan step has unknown action
        val scanResult = pirJobsRunner.runEligibleJobs(context, PirExecutionType.MANUAL)
        assertTrue("Scan should succeed overall (not crash)", scanResult.isSuccess)

        // Verify scan job was created
        val scanJobRecords = pirSchedulingRepository.getAllValidScanJobRecords()
        val unknownActionBrokerScanJob =
            scanJobRecords.find { it.brokerName == unknownScanActionBrokerName }

        assertTrue(
            "Unknown action broker should have scan job created",
            unknownActionBrokerScanJob != null,
        )

        // The scan is not executed at all because the step parsing fails.
        assertEquals(
            "Unknown action broker scan job status should be NOT_EXECUTED (parsing failed)",
            ScanJobStatus.NOT_EXECUTED,
            unknownActionBrokerScanJob?.status,
        )

        // Verify no actions were pushed (since scan step parsing failed)
        val pushedActions = fakePirMessagingInterface.pushedActions
        val brokerActions = pushedActions.filterIsInstance<BrokerAction.Navigate>()
            .filter { it.url.contains("fake-broker-scan-unknown-action") }
        assertTrue(
            "No actions should be pushed for broker with unknown scan action",
            brokerActions.isEmpty(),
        )

        // Verify no extracted profiles (scan didn't run)
        val extractedProfiles = pirRepository.getAllExtractedProfiles()
        val brokerProfiles = extractedProfiles.filter { it.brokerName == unknownScanActionBrokerName }
        assertTrue(
            "Should have no extracted profiles from unknown action broker",
            brokerProfiles.isEmpty(),
        )

        println("==================== STEP 3: Verify scan completed without crash ====================")

        // Verify scan completion pixels were fired (indicating graceful completion)
        assertPixelsWereFired(
            PirPixel.PIR_FOREGROUND_RUN_STARTED,
            PirPixel.PIR_FOREGROUND_RUN_COMPLETED,
        )
    }

    @Test
    fun testOptOutGracefullyHandlesUnknownActions() = runBlocking {
        println("==================== STEP 1: Setup broker with unknown opt-out action ====================")

        // This broker has a VALID scan step but INVALID opt-out step
        loadBrokerWithOptOutUnknownAction()

        // Save test profile
        pirRepository.replaceUserProfile(testProfile)

        // Verify broker is loaded
        val activeBrokers = pirRepository.getAllActiveBrokers()
        assertEquals("Should have 1 active broker", 1, activeBrokers.size)
        assertTrue(
            "Should be the unknown opt-out action broker",
            activeBrokers.contains(unknownOptOutActionBrokerName),
        )

        println("==================== STEP 2: Run scan - should succeed (scan step is valid) ====================")

        // Run scan - should succeed since the scan step is valid
        val scanResult = pirJobsRunner.runEligibleJobs(context, PirExecutionType.MANUAL)
        assertTrue("Scan should succeed", scanResult.isSuccess)

        // Check scan job record
        val scanJobRecords = pirSchedulingRepository.getAllValidScanJobRecords()
        val unknownActionBrokerScanJob =
            scanJobRecords.find { it.brokerName == unknownOptOutActionBrokerName }

        assertTrue(
            "Should have scan job for unknown opt-out action broker",
            unknownActionBrokerScanJob != null,
        )

        // Scan should succeed with MATCHES_FOUND since scan step is valid
        assertEquals(
            "Unknown opt-out action broker scan should have MATCHES_FOUND",
            ScanJobStatus.MATCHES_FOUND,
            unknownActionBrokerScanJob?.status,
        )

        // Verify extracted profiles
        val extractedProfiles = pirRepository.getAllExtractedProfiles()
        val brokerProfiles = extractedProfiles.filter { it.brokerName == unknownOptOutActionBrokerName }
        assertTrue(
            "Should have extracted profiles from broker",
            brokerProfiles.isNotEmpty(),
        )

        println("==================== STEP 3: Verify opt-out remains NOT_EXECUTED ====================")

        // Opt-out jobs should be created but remain NOT_EXECUTED because opt-out step parsing fails
        val optOutJobRecords = pirSchedulingRepository.getAllValidOptOutJobRecords()
        val unknownBrokerOptOutJobs =
            optOutJobRecords.filter { it.brokerName == unknownOptOutActionBrokerName }

        assertTrue(
            "Should have opt-out jobs for broker",
            unknownBrokerOptOutJobs.isNotEmpty(),
        )

        // Verify opt-out remains NOT_EXECUTED because parsing the opt-out step fails
        val unknownBrokerOptOutJob = unknownBrokerOptOutJobs.first()
        assertEquals(
            "Unknown action broker opt-out job should remain NOT_EXECUTED (opt-out parsing failed)",
            OptOutJobStatus.NOT_EXECUTED,
            unknownBrokerOptOutJob.status,
        )

        println("==================== STEP 4: Verify no opt-out actions pushed ====================")

        // Verify that no opt-out actions were pushed (only scan actions)
        val pushedActions = fakePirMessagingInterface.pushedActions
        val optOutNavigateActions = pushedActions
            .filterIsInstance<BrokerAction.Navigate>()
            .filter { it.url.contains("/optout") }

        assertTrue(
            "Should not have pushed opt-out navigate actions",
            optOutNavigateActions.isEmpty(),
        )

        println("==================== STEP 5: Verify the flow completed without crash ====================")

        // Verify scan completion pixels were fired (indicating graceful completion)
        assertPixelsWereFired(
            PirPixel.PIR_FOREGROUND_RUN_STARTED,
            PirPixel.PIR_FOREGROUND_RUN_COMPLETED,
        )
    }

    private fun createEventHandlers(): List<EventHandler> {
        // Create all event handlers with their real implementations
        return listOf(
            StartedEventHandler(),
            LoadUrlCompleteEventHandler(),
            LoadUrlFailedEventHandler(),
            ErrorReceivedHandler(pirRunStateHandler),
            RetryGetCaptchaSolutionEventHandler(),
            RetryAwaitCaptchaSolutionEventHandler(),
            JsActionSuccessEventHandler(pirRunStateHandler, fakeTimeProvider),
            BrokerStepCompletedEventHandler(pirRunStateHandler, fakeTimeProvider),
            BrokerActionFailedEventHandler(pirRunStateHandler, fakeTimeProvider),
            ExecuteBrokerStepActionEventHandler(pirRunStateHandler, fakeTimeProvider),
            ExecuteNextBrokerStepEventHandler(fakeTimeProvider, pirRunStateHandler),
            ConditionExpectationSucceededEventHandler(pirRunStateHandler, fakeTimeProvider),
            EmailReceivedEventHandler(pirRunStateHandler, fakeTimeProvider),
            CaptchaInfoReceivedEventHandler(fakeTimeProvider, pirRunStateHandler),
        )
    }

    private fun loadBrokers() {
        val activeBroker = parseBrokerJson("fake-broker.json")
        insertBrokerIntoDb(activeBroker, "fake-broker.json")

        val removedBroker = parseBrokerJson("fake-removed-broker.json")
        insertBrokerIntoDb(removedBroker, "fake-removed-broker.json")
    }

    private fun loadBrokerWithScanUnknownAction() {
        val unknownActionBroker = parseBrokerJson("fake-broker-scan-unknown-action.json")
        insertBrokerIntoDb(unknownActionBroker, "fake-broker-scan-unknown-action.json")
    }

    private fun loadBrokerWithOptOutUnknownAction() {
        val unknownActionBroker = parseBrokerJson("fake-broker-optout-unknown-action.json")
        insertBrokerIntoDb(unknownActionBroker, "fake-broker-optout-unknown-action.json")
    }

    private fun insertBrokerIntoDb(parsedBroker: ParsedBrokerConfig, fileName: String) {
        val db = databaseFactory.getDatabaseSync()
        val brokerDao = db.brokerDao()

        brokerDao.upsert(
            broker = BrokerEntity(
                name = parsedBroker.name,
                url = parsedBroker.url,
                version = parsedBroker.version,
                parent = null,
                addedDatetime = parsedBroker.addedDatetime,
                fileName = fileName,
                removedAt = parsedBroker.removedAt ?: 0L,
            ),
            brokerScan = BrokerScan(
                brokerName = parsedBroker.name,
                stepsJson = parsedBroker.scanStepJson,
            ),
            brokerOptOut = BrokerOptOut(
                brokerName = parsedBroker.name,
                optOutUrl = parsedBroker.optOutUrl,
                stepsJson = parsedBroker.optOutStepJson,
            ),
            schedulingConfig = BrokerSchedulingConfigEntity(
                brokerName = parsedBroker.name,
                retryError = parsedBroker.schedulingConfig.retryError,
                confirmOptOutScan = parsedBroker.schedulingConfig.confirmOptOutScan,
                maintenanceScan = parsedBroker.schedulingConfig.maintenanceScan,
                maxAttempts = parsedBroker.schedulingConfig.maxAttempts,
            ),
            mirrorSiteEntity = emptyList(),
        )
    }

    private fun assertPixelsWereFired(vararg expectedPixels: PirPixel) {
        expectedPixels.forEach { pixel ->
            assertTrue(
                "Expected pixel ${pixel.name} was not fired",
                fakePixel.wasPixelFired(pixel),
            )
        }
    }

    private suspend fun assertCorrectActionsWerePushedToJsLayer(
        scanStepActionsFilter: (List<BrokerAction>) -> List<BrokerAction>,
        optOutStepActionsFilter: (List<BrokerAction>) -> List<BrokerAction>,
        additionalAssertionsOnPushedActions: (List<BrokerAction>) -> Unit = {},
    ) {
        // Verifies that all actions were processed and pushed to the JS layer in correct order
        val pushedActions = fakePirMessagingInterface.pushedActions
        val expectedScanSteps = brokerStepsParser.parseStep(
            pirRepository.getBrokerForName(activeBrokerName)!!,
            pirRepository.getBrokerScanSteps(activeBrokerName)!!,
            testProfile.id,
        )
        val expectedOptOutSteps = brokerStepsParser.parseStep(
            pirRepository.getBrokerForName(activeBrokerName)!!,
            pirRepository.getBrokerOptOutSteps(activeBrokerName)!!,
            testProfile.id,
        )
        val combinedExpectedActions =
            scanStepActionsFilter(expectedScanSteps.first().step.actions) +
                optOutStepActionsFilter(expectedOptOutSteps.first().step.actions)

        assertEquals(combinedExpectedActions.size, pushedActions.size)
        combinedExpectedActions.forEachIndexed { index, expectedAction ->
            val pushedAction = pushedActions.getOrNull(index)
            assertTrue("Expected action was pushed at index $index", pushedAction != null)
            assertEquals(
                "Pushed action at index $index should match expected action",
                expectedAction::class,
                pushedAction!!::class,
            )
            assertEquals(
                "Pushed action ID at index $index should match expected action ID",
                expectedAction.id,
                pushedAction.id,
            )
        }

        additionalAssertionsOnPushedActions(pushedActions)
    }

    private fun parseBrokerJson(resourceName: String): ParsedBrokerConfig {
        val jsonString = loadResourceAsString(resourceName)

        // Create a Moshi instance for parsing the broker config
        val brokerMoshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        val adapter = brokerMoshi.adapter(TestBrokerConfig::class.java)
        val config = adapter.fromJson(jsonString) ?: error("Failed to parse broker JSON: $resourceName")

        // Convert step objects back to JSON strings for storage
        val stepAdapter = brokerMoshi.adapter(Map::class.java)
        val scanStep = config.steps.first { (it["stepType"] as? String) == "scan" }
        val optOutStep = config.steps.first { (it["stepType"] as? String) == "optOut" }

        return ParsedBrokerConfig(
            name = config.name,
            url = config.url,
            version = config.version,
            addedDatetime = config.addedDatetime,
            optOutUrl = config.optOutUrl,
            scanStepJson = stepAdapter.toJson(scanStep),
            optOutStepJson = stepAdapter.toJson(optOutStep),
            schedulingConfig = config.schedulingConfig,
            removedAt = config.removedAt,
        )
    }

    private fun loadResourceAsString(resourceName: String): String {
        return javaClass.classLoader!!.getResourceAsStream(resourceName)!!
            .bufferedReader()
            .use { it.readText() }
    }

    @JsonClass(generateAdapter = true)
    data class TestBrokerConfig(
        val name: String,
        val url: String,
        val version: String,
        val addedDatetime: Long,
        val optOutUrl: String,
        val steps: List<Map<String, Any>>,
        val schedulingConfig: TestSchedulingConfig,
        val removedAt: Long?,
    )

    @JsonClass(generateAdapter = true)
    data class TestSchedulingConfig(
        val retryError: Int,
        val confirmOptOutScan: Int,
        val maintenanceScan: Int,
        val maxAttempts: Int,
    )

    data class ParsedBrokerConfig(
        val name: String,
        val url: String,
        val version: String,
        val addedDatetime: Long,
        val optOutUrl: String,
        val scanStepJson: String,
        val optOutStepJson: String,
        val schedulingConfig: TestSchedulingConfig,
        val removedAt: Long?,
    )
}
