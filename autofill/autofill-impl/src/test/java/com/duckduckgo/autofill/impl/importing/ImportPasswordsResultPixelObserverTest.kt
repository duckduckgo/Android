package com.duckduckgo.autofill.impl.importing

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.autofill.api.AutofillImportLaunchSource.Onboarding
import com.duckduckgo.autofill.impl.importing.CredentialImporter.ImportResult.Finished
import com.duckduckgo.autofill.impl.importing.CredentialImporter.ImportResult.InProgress
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.ImportPasswordsPixelSender
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class ImportPasswordsResultPixelObserverTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val credentialImporter: CredentialImporter = mock()
    private val importPasswordsPixelSender: ImportPasswordsPixelSender = mock()
    private val lifecycleOwner: LifecycleOwner = mock()

    private val testee = ImportPasswordsResultPixelObserver(
        credentialImporter = credentialImporter,
        importPasswordsPixelSender = importPasswordsPixelSender,
        appCoroutineScope = coroutineTestRule.testScope,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenNoImportStatusThenNoPixelSent() = runTest {
        whenever(credentialImporter.getImportStatus()).thenReturn(emptyFlow())
        testee.onCreate(lifecycleOwner)
        verifyNoInteractions(importPasswordsPixelSender)
    }

    @Test
    fun whenImportStillInProgressThenNoPixelSent() = runTest {
        whenever(credentialImporter.getImportStatus()).thenReturn(listOf(InProgress).asFlow())
        testee.onCreate(lifecycleOwner)
        verifyNoInteractions(importPasswordsPixelSender)
    }

    @Test
    fun whenImportFinishedThenSuccessPixelSentWithCountsAndLaunchSource() = runTest {
        whenever(credentialImporter.getImportStatus()).thenReturn(
            listOf(InProgress, Finished(savedCredentials = 10, numberSkipped = 2, source = Onboarding)).asFlow(),
        )

        testee.onCreate(lifecycleOwner)

        verify(importPasswordsPixelSender).onImportSuccessful(savedCredentials = 10, numberSkipped = 2, source = Onboarding)
    }
}
