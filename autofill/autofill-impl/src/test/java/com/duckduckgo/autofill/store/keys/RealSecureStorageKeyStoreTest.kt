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

package com.duckduckgo.autofill.store.keys

import android.annotation.SuppressLint
import android.content.SharedPreferences
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames
import com.duckduckgo.autofill.impl.securestorage.SecureStorageException
import com.duckduckgo.autofill.impl.service.AutofillServiceFeature
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi") // fake toggle store
class RealSecureStorageKeyStoreTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val pixel: Pixel = mock()
    private lateinit var autofillFeature: AutofillFeature
    private lateinit var autofillServiceFeature: AutofillServiceFeature
    private lateinit var sharedPreferencesProvider: SharedPreferencesProvider

    private lateinit var legacyPrefs: SharedPreferences
    private lateinit var legacyEditor: SharedPreferences.Editor
    private lateinit var harmonyPrefs: SharedPreferences
    private lateinit var harmonyEditor: SharedPreferences.Editor
    private lateinit var encryptedPreferencesFactory: FakeEncryptedPreferencesFactory

    private lateinit var testee: RealSecureStorageKeyStore

    @Before
    fun setup() {
        autofillFeature = FakeFeatureToggleFactory.create(AutofillFeature::class.java)
        autofillServiceFeature = FakeFeatureToggleFactory.create(AutofillServiceFeature::class.java)
        sharedPreferencesProvider = mock()

        legacyEditor = mock {
            on { putString(any(), any()) } doReturn it
            on { remove(any()) } doReturn it
            on { commit() } doReturn true
        }
        legacyPrefs = mock {
            on { edit() } doReturn legacyEditor
        }

        harmonyEditor = mock {
            on { putString(any(), any()) } doReturn it
            on { remove(any()) } doReturn it
            on { commit() } doReturn true
        }
        harmonyPrefs = mock {
            on { edit() } doReturn harmonyEditor
        }

        encryptedPreferencesFactory = FakeEncryptedPreferencesFactory(legacyPrefs)
        configureHarmonyDisabled()
    }

    private fun createTestee() {
        testee = RealSecureStorageKeyStore(
            coroutineScope = coroutineTestRule.testScope,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            autofillFeature = autofillFeature,
            autofillServiceFeature = autofillServiceFeature,
            sharedPreferencesProvider = sharedPreferencesProvider,
            pixel = pixel,
            encryptedPreferencesFactory = encryptedPreferencesFactory,
        )
    }

    // region Basic key operations without Harmony

    @Test
    fun whenUpdateKeyCalledThenKeyIsStoredInLegacyPrefs() = runTest {
        configureHarmonyDisabled()
        createTestee()

        testee.updateKey(KEY_NAME, TEST_VALUE)

        verify(legacyEditor).putString(eq(KEY_NAME), eq(TEST_VALUE.toByteString().base64()))
        verify(legacyEditor).commit()
    }

    @Test
    fun whenGetKeyCalledThenKeyIsRetrievedFromLegacyPrefs() = runTest {
        configureHarmonyDisabled()
        whenever(legacyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(TEST_VALUE.toByteString().base64())
        createTestee()

        val result = testee.getKey(KEY_NAME)

        assertArrayEquals(TEST_VALUE, result)
        verify(legacyPrefs).getString(eq(KEY_NAME), eq(null))
    }

    @Test
    fun whenGetKeyCalledForNonExistentKeyThenReturnsNull() = runTest {
        configureHarmonyDisabled()
        whenever(legacyPrefs.getString(any(), anyOrNull())).thenReturn(null)
        createTestee()

        val result = testee.getKey(KEY_NAME)

        assertNull(result)
    }

    // endregion

    // region canUseEncryption

    @Test
    fun whenLegacyPrefsAvailableAndHarmonyDisabledThenCanUseEncryptionReturnsTrue() = runTest {
        configureHarmonyDisabled()
        createTestee()

        assertTrue(testee.canUseEncryption())
    }

    @Test
    fun whenLegacyPrefsThrowsAndHarmonyDisabledThenCanUseEncryptionReturnsFalse() = runTest {
        configureHarmonyDisabled()
        encryptedPreferencesFactory = FakeEncryptedPreferencesFactory(exception = RuntimeException("Failed"))
        createTestee()

        assertFalse(testee.canUseEncryption())
    }

    @Test
    fun whenBothPrefsAvailableAndHarmonyEnabledThenCanUseEncryptionReturnsTrue() = runTest {
        configureHarmonyEnabled()
        createTestee()

        assertTrue(testee.canUseEncryption())
    }

    @Test
    fun whenLegacyPrefsThrowsAndHarmonyEnabledThenCanUseEncryptionReturnsFalse() = runTest {
        configureHarmonyEnabled()
        encryptedPreferencesFactory = FakeEncryptedPreferencesFactory(exception = RuntimeException("Failed"))
        createTestee()

        assertFalse(testee.canUseEncryption())
    }

    @Test
    fun whenHarmonyPrefsNullAndHarmonyEnabledThenCanUseEncryptionReturnsFalse() = runTest {
        configureHarmonyEnabled(harmonyPrefsReturnsNull = true)
        createTestee()

        assertFalse(testee.canUseEncryption())
    }

    // endregion

    // region Harmony dual-write

    @Test
    fun whenHarmonyEnabledThenUpdateKeyWritesToBothStores() = runTest {
        configureHarmonyEnabled()
        createTestee()

        testee.updateKey(KEY_NAME, TEST_VALUE)

        val expectedBase64 = TEST_VALUE.toByteString().base64()
        verify(legacyEditor).putString(eq(KEY_NAME), eq(expectedBase64))
        verify(harmonyEditor).putString(eq(KEY_NAME), eq(expectedBase64))
    }

    // endregion

    // region Write guard (key already exists)

    @Test
    fun whenHarmonyEnabledAndKeyAlreadyExistsInLegacyThenWriteIsBlockedAndExceptionThrown() = runTest {
        configureHarmonyEnabled()
        whenever(legacyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(EXISTING_VALUE.toByteString().base64())
        createTestee()

        var exceptionThrown = false
        try {
            testee.updateKey(KEY_NAME, TEST_VALUE)
        } catch (e: SecureStorageException.KeyAlreadyExistsException) {
            exceptionThrown = true
            assertEquals("Trying to overwrite already existing key", e.message)
        }

        assertTrue(exceptionThrown)
        verify(pixel).fire(eq(AutofillPixelNames.AUTOFILL_STORE_KEY_ALREADY_EXISTS), any(), any(), any())
    }

    @Test
    fun whenHarmonyEnabledAndKeyAlreadyExistsInHarmonyThenWriteIsBlockedAndExceptionThrown() = runTest {
        configureHarmonyEnabled()
        whenever(legacyPrefs.getString(any(), anyOrNull())).thenReturn(null)
        whenever(harmonyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(EXISTING_VALUE.toByteString().base64())
        createTestee()

        var exceptionThrown = false
        try {
            testee.updateKey(KEY_NAME, TEST_VALUE)
        } catch (e: SecureStorageException.KeyAlreadyExistsException) {
            exceptionThrown = true
        }

        assertTrue(exceptionThrown)
        verify(pixel).fire(eq(AutofillPixelNames.AUTOFILL_STORE_KEY_ALREADY_EXISTS), any(), any(), any())
    }

    @Test
    fun whenHarmonyDisabledAndKeyAlreadyExistsThenPixelFiredAndThrowException() = runTest {
        configureHarmonyDisabled()
        whenever(legacyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(EXISTING_VALUE.toByteString().base64())
        createTestee()

        var exceptionThrown = false
        try {
            testee.updateKey(KEY_NAME, TEST_VALUE)
        } catch (e: SecureStorageException.KeyAlreadyExistsException) {
            exceptionThrown = true
        }

        assertTrue(exceptionThrown)
        verify(pixel).fire(eq(AutofillPixelNames.AUTOFILL_STORE_KEY_ALREADY_EXISTS), any(), any(), any())
    }

    // endregion

    // region Preferences retrieval failure pixels

    @Test
    fun whenLegacyPrefsCreationFailsThenPixelFired() = runTest {
        configureHarmonyDisabled()
        encryptedPreferencesFactory = FakeEncryptedPreferencesFactory(exception = RuntimeException("Keystore failed"))
        createTestee()

        testee.canUseEncryption()

        verify(pixel).fire(eq(AutofillPixelNames.AUTOFILL_PREFERENCES_RETRIEVAL_FAILED), any(), any(), any())
    }

    // endregion

    // region Null preferences handling

    @Test
    fun whenLegacyPrefsThrowsOnWriteAndHarmonyEnabledThenExceptionThrown() = runTest {
        configureHarmonyEnabled()
        encryptedPreferencesFactory = FakeEncryptedPreferencesFactory(exception = RuntimeException("Failed"))
        createTestee()

        var exceptionThrown = false
        try {
            testee.updateKey(KEY_NAME, TEST_VALUE)
        } catch (e: SecureStorageException.InternalSecureStorageException) {
            exceptionThrown = true
            assertEquals("Legacy Preferences file is null on write", e.message)
        }

        assertTrue(exceptionThrown)
        verify(pixel).fire(eq(AutofillPixelNames.AUTOFILL_PREFERENCES_UPDATE_KEY_NULL_FILE), any(), any(), any())
    }

    @Test
    fun whenHarmonyPrefsNullOnWriteAndHarmonyEnabledThenExceptionThrown() = runTest {
        configureHarmonyEnabled(harmonyPrefsReturnsNull = true)
        createTestee()

        var exceptionThrown = false
        try {
            testee.updateKey(KEY_NAME, TEST_VALUE)
        } catch (e: SecureStorageException.InternalSecureStorageException) {
            exceptionThrown = true
            assertEquals("Harmony Preferences file is null on write", e.message)
        }

        assertTrue(exceptionThrown)
        verify(pixel).fire(eq(AutofillPixelNames.AUTOFILL_HARMONY_PREFERENCES_UPDATE_KEY_NULL_FILE), any(), any(), any())
    }

    @Test
    fun whenLegacyPrefsThrowsOnWriteAndHarmonyDisabledThenPixelFiredAndThrowException() = runTest {
        configureHarmonyDisabled()
        encryptedPreferencesFactory = FakeEncryptedPreferencesFactory(exception = RuntimeException("Failed"))
        createTestee()

        var exceptionThrown = false
        try {
            testee.updateKey(KEY_NAME, TEST_VALUE)
        } catch (e: SecureStorageException.InternalSecureStorageException) {
            exceptionThrown = true
            assertEquals("Legacy Preferences file is null on write", e.message)
        }

        assertTrue(exceptionThrown)
        verify(pixel).fire(eq(AutofillPixelNames.AUTOFILL_PREFERENCES_UPDATE_KEY_NULL_FILE), any(), any(), any())
    }

    @Test
    fun whenLegacyPrefsThrowsOnReadAndReadFromHarmonyEnabledThenExceptionThrown() = runTest {
        configureReadFromHarmonyEnabled()
        encryptedPreferencesFactory = FakeEncryptedPreferencesFactory(exception = RuntimeException("Failed"))
        createTestee()

        var exceptionThrown = false
        try {
            testee.getKey(KEY_NAME)
        } catch (e: SecureStorageException.InternalSecureStorageException) {
            exceptionThrown = true
            assertEquals("Legacy Preferences file is null on read", e.message)
        }

        assertTrue(exceptionThrown)
        verify(pixel).fire(eq(AutofillPixelNames.AUTOFILL_PREFERENCES_GET_KEY_NULL_FILE), any(), any(), any())
    }

    @Test
    fun whenHarmonyPrefsNullOnReadAndReadFromHarmonyEnabledThenExceptionThrown() = runTest {
        configureReadFromHarmonyEnabled(harmonyPrefsReturnsNull = true)
        createTestee()

        var exceptionThrown = false
        try {
            testee.getKey(KEY_NAME)
        } catch (e: SecureStorageException.InternalSecureStorageException) {
            exceptionThrown = true
            assertEquals("Harmony Preferences file is null on read", e.message)
        }

        assertTrue(exceptionThrown)
    }

    // endregion

    // region Harmony value comparison (diagnostic pixels)

    @Test
    fun whenHarmonyEnabledAndValuesMatchThenNoMismatchPixelFired() = runTest {
        configureHarmonyEnabled()
        val base64Value = TEST_VALUE.toByteString().base64()
        whenever(legacyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(base64Value)
        whenever(harmonyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(base64Value)
        createTestee()

        testee.getKey(KEY_NAME)

        verify(pixel, never()).fire(eq(AutofillPixelNames.AUTOFILL_HARMONY_KEY_MISMATCH), any(), any(), any())
        verify(pixel, never()).fire(eq(AutofillPixelNames.AUTOFILL_HARMONY_KEY_MISSING), any(), any(), any())
        verify(pixel, never()).fire(eq(AutofillPixelNames.AUTOFILL_PREFERENCES_KEY_MISSING), any(), any(), any())
    }

    @Test
    fun whenHarmonyKeyMissingButLegacyHasValueThenMissingPixelFired() = runTest {
        configureHarmonyEnabled()
        whenever(legacyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(TEST_VALUE.toByteString().base64())
        whenever(harmonyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(null)
        createTestee()

        testee.getKey(KEY_NAME)

        verify(pixel).fire(eq(AutofillPixelNames.AUTOFILL_HARMONY_KEY_MISSING), any(), any(), any())
    }

    @Test
    fun whenLegacyKeyMissingButHarmonyHasValueThenMissingPixelFired() = runTest {
        configureHarmonyEnabled()
        whenever(legacyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(null)
        whenever(harmonyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(TEST_VALUE.toByteString().base64())
        createTestee()

        testee.getKey(KEY_NAME)

        verify(pixel).fire(eq(AutofillPixelNames.AUTOFILL_PREFERENCES_KEY_MISSING), any(), any(), any())
    }

    @Test
    fun whenHarmonyAndLegacyValuesDifferThenMismatchPixelFired() = runTest {
        configureHarmonyEnabled()
        whenever(legacyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(TEST_VALUE.toByteString().base64())
        whenever(harmonyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(DIFFERENT_VALUE.toByteString().base64())
        createTestee()

        testee.getKey(KEY_NAME)

        verify(pixel).fire(eq(AutofillPixelNames.AUTOFILL_HARMONY_KEY_MISMATCH), any(), any(), any())
    }

    @Test
    fun whenHarmonyKeyMissingAndReadFromHarmonyEnabledThenExceptionThrown() = runTest {
        configureReadFromHarmonyEnabled()
        whenever(legacyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(TEST_VALUE.toByteString().base64())
        whenever(harmonyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(null)
        createTestee()

        var exceptionThrown = false
        try {
            testee.getKey(KEY_NAME)
        } catch (e: SecureStorageException.InternalSecureStorageException) {
            exceptionThrown = true
            assertEquals("Harmony key missing", e.message)
        }

        assertTrue(exceptionThrown)
    }

    @Test
    fun whenLegacyKeyMissingAndReadFromHarmonyEnabledThenExceptionThrown() = runTest {
        configureReadFromHarmonyEnabled()
        whenever(legacyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(null)
        whenever(harmonyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(TEST_VALUE.toByteString().base64())
        createTestee()

        var exceptionThrown = false
        try {
            testee.getKey(KEY_NAME)
        } catch (e: SecureStorageException.InternalSecureStorageException) {
            exceptionThrown = true
            assertEquals("Legacy key missing", e.message)
        }

        assertTrue(exceptionThrown)
    }

    @Test
    fun whenKeysMismatchAndReadFromHarmonyEnabledThenExceptionThrown() = runTest {
        configureReadFromHarmonyEnabled()
        whenever(legacyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(TEST_VALUE.toByteString().base64())
        whenever(harmonyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(DIFFERENT_VALUE.toByteString().base64())
        createTestee()

        var exceptionThrown = false
        try {
            testee.getKey(KEY_NAME)
        } catch (e: SecureStorageException.InternalSecureStorageException) {
            exceptionThrown = true
            assertEquals("Harmony key mismatch", e.message)
        }

        assertTrue(exceptionThrown)
    }

    // endregion

    // region readFromHarmony

    @Test
    fun whenReadFromHarmonyEnabledAndValuesMatchThenReturnsHarmonyValue() = runTest {
        configureReadFromHarmonyEnabled()
        val base64Value = TEST_VALUE.toByteString().base64()
        whenever(legacyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(base64Value)
        whenever(harmonyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(base64Value)
        createTestee()

        val result = testee.getKey(KEY_NAME)

        assertArrayEquals(TEST_VALUE, result)
    }

    @Test
    fun whenReadFromHarmonyDisabledThenReturnsLegacyValue() = runTest {
        configureHarmonyEnabled() // useHarmony ON but readFromHarmony OFF
        val base64Value = TEST_VALUE.toByteString().base64()
        whenever(legacyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(base64Value)
        whenever(harmonyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(base64Value)
        createTestee()

        val result = testee.getKey(KEY_NAME)

        assertArrayEquals(TEST_VALUE, result)
    }

    @Test
    fun whenHarmonyDisabledThenDoesNotReadFromHarmony() = runTest {
        configureHarmonyDisabled()
        whenever(legacyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(TEST_VALUE.toByteString().base64())
        createTestee()

        val result = testee.getKey(KEY_NAME)

        // Should return legacy value, no mismatch pixel since harmony is disabled
        assertArrayEquals(TEST_VALUE, result)
        verify(harmonyPrefs, never()).getString(any(), any())
        verify(pixel, never()).fire(eq(AutofillPixelNames.AUTOFILL_HARMONY_KEY_MISMATCH), any(), any(), any())
    }

    // endregion

    // region Rollback on harmony write failure

    @Test
    fun whenHarmonyWriteFailsThenLegacyWriteIsRolledBack() = runTest {
        autofillFeature.useHarmony().setRawStoredState(State(enable = true))
        autofillFeature.readFromHarmony().setRawStoredState(State(enable = false))

        val failingHarmonyEditor: SharedPreferences.Editor = mock {
            on { putString(any(), any()) } doReturn it
            on { commit() } doThrow RuntimeException("Simulated harmony write failure")
        }
        val failingHarmonyPrefs: SharedPreferences = mock {
            on { edit() } doReturn failingHarmonyEditor
        }
        sharedPreferencesProvider.stub {
            onBlocking { getMigratedEncryptedSharedPreferences(any(), any()) } doReturn failingHarmonyPrefs
        }
        createTestee()

        var exceptionThrown = false
        try {
            testee.updateKey(KEY_NAME, TEST_VALUE)
        } catch (e: Exception) {
            exceptionThrown = true
        }

        assertTrue(exceptionThrown)
        // Legacy should be rolled back (key removed)
        verify(legacyEditor).remove(KEY_NAME)
        verify(pixel).fire(eq(AutofillPixelNames.AUTOFILL_HARMONY_PREFERENCES_UPDATE_KEY_FAILED), any(), any(), any())
    }

    // endregion

    // region Fix — explicit harmony failure handling

    @Test
    fun whenLegacyCommitReturnsFalseThenUpdateKeyThrowsAndFiresFailurePixel() = runTest {
        configureHarmonyDisabled()
        whenever(legacyPrefs.getString(any(), anyOrNull())).thenReturn(null) // key absent — write guard passes
        whenever(legacyEditor.commit()).thenReturn(false) // commit() returns false without throwing (e.g. fsync failure)
        createTestee()

        var exceptionThrown = false
        try {
            testee.updateKey(KEY_NAME, TEST_VALUE)
        } catch (e: SecureStorageException.InternalSecureStorageException) {
            exceptionThrown = true
        }

        assertTrue(exceptionThrown)
        verify(pixel).fire(eq(AutofillPixelNames.AUTOFILL_PREFERENCES_UPDATE_KEY_FAILED), any(), any(), any())
    }

    @Test
    fun whenHarmonyCommitReturnsFalseThenUpdateKeyThrowsAndFiresFailurePixel() = runTest {
        configureHarmonyEnabled()
        val failingHarmonyEditor: SharedPreferences.Editor = mock {
            on { putString(any(), any()) } doReturn it
            on { remove(any()) } doReturn it
            on { commit() } doReturn false // returns false without throwing (e.g. fsync failure)
        }
        val failingHarmonyPrefs: SharedPreferences = mock {
            on { edit() } doReturn failingHarmonyEditor
            on { getString(any(), anyOrNull()) } doReturn null // key absent — write guard passes
        }
        sharedPreferencesProvider.stub {
            onBlocking { getMigratedEncryptedSharedPreferences(any(), any()) } doReturn failingHarmonyPrefs
        }
        createTestee()

        var exceptionThrown = false
        try {
            testee.updateKey(KEY_NAME, TEST_VALUE)
        } catch (e: SecureStorageException.InternalSecureStorageException) {
            exceptionThrown = true
        }

        assertTrue(exceptionThrown)
        verify(pixel).fire(eq(AutofillPixelNames.AUTOFILL_HARMONY_PREFERENCES_UPDATE_KEY_FAILED), any(), any(), any())
        // Legacy write should be rolled back since harmony write failed
        verify(legacyEditor).remove(KEY_NAME)
    }

    @Test
    fun whenHarmonyGetKeyThrowsThenFiresPixelAndRethrows() = runTest {
        configureReadFromHarmonyEnabled()
        whenever(legacyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(TEST_VALUE.toByteString().base64())
        whenever(harmonyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenThrow(RuntimeException("Keystore transient fault"))
        createTestee()

        var exceptionThrown = false
        try {
            testee.getKey(KEY_NAME)
        } catch (e: SecureStorageException.InternalSecureStorageException) {
            exceptionThrown = true
        }

        assertTrue(exceptionThrown)
        verify(pixel).fire(eq(AutofillPixelNames.AUTOFILL_HARMONY_PREFERENCES_GET_KEY_FAILED), any(), any(), any())
    }

    @Test
    fun whenHarmonyKeyAlreadyExistsCheckThrowsThenWriteIsBlockedByWriteGuard() = runTest {
        configureHarmonyEnabled()
        whenever(legacyPrefs.getString(any(), anyOrNull())).thenReturn(null)
        whenever(harmonyPrefs.getString(any(), anyOrNull())).thenThrow(RuntimeException("Transient Keystore fault"))
        createTestee()

        var exceptionThrown = false
        try {
            testee.updateKey(KEY_NAME, TEST_VALUE)
        } catch (e: SecureStorageException.KeyAlreadyExistsException) {
            exceptionThrown = true
        }

        assertTrue(exceptionThrown)
        // Conservative: exception during harmony existence check is treated as "key exists" — write is blocked
        verify(pixel).fire(eq(AutofillPixelNames.AUTOFILL_STORE_KEY_ALREADY_EXISTS), any(), any(), any())
    }

    @Test
    fun whenLegacyKeyAlreadyExistsCheckThrowsThenWriteIsNotBlocked() = runTest {
        configureHarmonyEnabled()
        whenever(legacyPrefs.getString(any(), anyOrNull())).thenThrow(RuntimeException("Legacy read fault"))
        whenever(harmonyPrefs.getString(any(), anyOrNull())).thenReturn(null) // key not in harmony either
        createTestee()

        // Legacy exception in keyAlreadyExists() returns false (structural failure, not transient Keystore issue)
        // so the write should proceed without triggering the write guard
        testee.updateKey(KEY_NAME, TEST_VALUE)

        verify(pixel, never()).fire(eq(AutofillPixelNames.AUTOFILL_STORE_KEY_ALREADY_EXISTS), any(), any(), any())
        verify(legacyEditor).putString(eq(KEY_NAME), any())
    }

    @Test
    fun whenLegacyGetKeyReturnsNonNullButDecodeFailsThenThrowsAndFiresDecodePixel() = runTest {
        configureHarmonyDisabled()
        whenever(legacyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(INVALID_BASE64)
        createTestee()

        var exceptionThrown = false
        try {
            testee.getKey(KEY_NAME)
        } catch (e: SecureStorageException.InternalSecureStorageException) {
            exceptionThrown = true
            assertEquals("Legacy preferences key value is present but cannot be decoded", e.message)
        }

        assertTrue(exceptionThrown)
        verify(pixel).fire(eq(AutofillPixelNames.AUTOFILL_PREFERENCES_GET_KEY_DECODE_FAILED), any(), any(), any())
    }

    @Test
    fun whenHarmonyGetKeyReturnsNonNullButDecodeFailsAndReadFromHarmonyThenThrowsAndFiresDecodePixel() = runTest {
        configureReadFromHarmonyEnabled()
        whenever(legacyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(TEST_VALUE.toByteString().base64())
        whenever(harmonyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(INVALID_BASE64)
        createTestee()

        var exceptionThrown = false
        try {
            testee.getKey(KEY_NAME)
        } catch (e: SecureStorageException.InternalSecureStorageException) {
            exceptionThrown = true
            assertEquals("Harmony preferences key value is present but cannot be decoded", e.message)
        }

        assertTrue(exceptionThrown)
        verify(pixel).fire(eq(AutofillPixelNames.AUTOFILL_HARMONY_PREFERENCES_GET_KEY_DECODE_FAILED), any(), any(), any())
    }

    @Test
    fun whenHarmonyGetKeyReturnsNonNullButDecodeFailsAndNotReadFromHarmonyThenFiresDecodePixelAndReturnsLegacyValue() = runTest {
        configureHarmonyEnabled() // useHarmony=true, readFromHarmony=false
        whenever(legacyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(TEST_VALUE.toByteString().base64())
        whenever(harmonyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(INVALID_BASE64)
        createTestee()

        val result = testee.getKey(KEY_NAME)

        assertArrayEquals(TEST_VALUE, result)
        verify(pixel).fire(eq(AutofillPixelNames.AUTOFILL_HARMONY_PREFERENCES_GET_KEY_DECODE_FAILED), any(), any(), any())
    }

    @Test
    fun whenUseHarmonyFlipsDuringGetKeyCallThenSnapshotPreventsReturningNull() = runTest {
        // Simulate TOCTOU: useHarmony() returns false on the first call (snapshot), true on all subsequent calls.
        // then readFromHarmony()=true would return harmonyValue (null) instead of legacyValue.
        val useHarmonyToggle: Toggle = mock()
        whenever(useHarmonyToggle.isEnabled()).thenReturn(false, true)
        val readFromHarmonyToggle: Toggle = mock()
        whenever(readFromHarmonyToggle.isEnabled()).thenReturn(true)
        val mockAutofillFeature: AutofillFeature = mock {
            on { useHarmony() } doReturn useHarmonyToggle
            on { readFromHarmony() } doReturn readFromHarmonyToggle
        }
        whenever(legacyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(TEST_VALUE.toByteString().base64())
        testee = RealSecureStorageKeyStore(
            coroutineScope = coroutineTestRule.testScope,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            autofillFeature = mockAutofillFeature,
            autofillServiceFeature = autofillServiceFeature,
            sharedPreferencesProvider = sharedPreferencesProvider,
            pixel = pixel,
            encryptedPreferencesFactory = encryptedPreferencesFactory,
        )

        val result = testee.getKey(KEY_NAME)

        assertArrayEquals(TEST_VALUE, result)
    }

    // endregion

    // region Multi-process mode (autofill service enabled)

    @Test
    fun whenMultiProcessModeEnabledThenGetKeyReadsOnlyFromHarmony() = runTest {
        configureMultiProcessMode()
        whenever(harmonyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(TEST_VALUE.toByteString().base64())
        createTestee()

        val result = testee.getKey(KEY_NAME)

        assertArrayEquals(TEST_VALUE, result)
        // Legacy must never be touched
        verify(legacyPrefs, never()).getString(any(), anyOrNull())
    }

    @Test
    fun whenMultiProcessModeEnabledAndKeyAbsentInHarmonyThenGetKeyReturnsNull() = runTest {
        configureMultiProcessMode()
        whenever(harmonyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(null)
        createTestee()

        val result = testee.getKey(KEY_NAME)

        assertNull(result)
        verify(legacyPrefs, never()).getString(any(), anyOrNull())
    }

    @Test
    fun whenMultiProcessModeEnabledThenUpdateKeyWritesToBothForRollbackSupport() = runTest {
        configureMultiProcessMode()
        whenever(harmonyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(null)
        createTestee()

        testee.updateKey(KEY_NAME, TEST_VALUE)

        val expectedBase64 = TEST_VALUE.toByteString().base64()
        // Both stores should be written to: Harmony for runtime, legacy for rollback
        verify(harmonyEditor).putString(eq(KEY_NAME), eq(expectedBase64))
        verify(harmonyEditor).commit()
        verify(legacyEditor).putString(eq(KEY_NAME), eq(expectedBase64))
        verify(legacyEditor).commit()
    }

    @Test
    fun whenMultiProcessModeEnabledAndKeyExistsInLegacyOnlyThenWriteIsNotBlocked() = runTest {
        // Key exists in legacy (stale cache) but not in harmony — write should proceed
        // because keyAlreadyExists skips legacy read in multi-process mode
        configureMultiProcessMode()
        whenever(legacyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(EXISTING_VALUE.toByteString().base64())
        whenever(harmonyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(null)
        createTestee()

        testee.updateKey(KEY_NAME, TEST_VALUE)

        // Write should succeed — legacy stale cache doesn't block
        verify(harmonyEditor).putString(eq(KEY_NAME), any())
        verify(legacyEditor).putString(eq(KEY_NAME), any())
        verify(pixel, never()).fire(eq(AutofillPixelNames.AUTOFILL_STORE_KEY_ALREADY_EXISTS), any(), any(), any())
    }

    @Test
    fun whenMultiProcessModeEnabledAndKeyExistsInHarmonyThenWriteIsBlocked() = runTest {
        configureMultiProcessMode()
        whenever(harmonyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(EXISTING_VALUE.toByteString().base64())
        createTestee()

        var exceptionThrown = false
        try {
            testee.updateKey(KEY_NAME, TEST_VALUE)
        } catch (e: SecureStorageException.KeyAlreadyExistsException) {
            exceptionThrown = true
        }

        assertTrue(exceptionThrown)
        verify(pixel).fire(eq(AutofillPixelNames.AUTOFILL_STORE_KEY_ALREADY_EXISTS), any(), any(), any())
    }

    @Test
    fun whenMultiProcessModeEnabledAndHarmonyNullOnGetKeyThenThrows() = runTest {
        configureMultiProcessMode(harmonyPrefsReturnsNull = true)
        createTestee()

        var exceptionThrown = false
        try {
            testee.getKey(KEY_NAME)
        } catch (e: SecureStorageException.InternalSecureStorageException) {
            exceptionThrown = true
        }

        assertTrue(exceptionThrown)
        verify(pixel).fire(eq(AutofillPixelNames.AUTOFILL_HARMONY_PREFERENCES_GET_KEY_NULL_FILE), any(), any(), any())
    }

    @Test
    fun whenMultiProcessModeEnabledAndHarmonyNullOnUpdateKeyThenThrows() = runTest {
        configureMultiProcessMode(harmonyPrefsReturnsNull = true)
        createTestee()

        var exceptionThrown = false
        try {
            testee.updateKey(KEY_NAME, TEST_VALUE)
        } catch (e: SecureStorageException.InternalSecureStorageException) {
            exceptionThrown = true
        }

        assertTrue(exceptionThrown)
        verify(pixel).fire(eq(AutofillPixelNames.AUTOFILL_HARMONY_PREFERENCES_UPDATE_KEY_NULL_FILE), any(), any(), any())
    }

    @Test
    fun whenMultiProcessModeDisabledThenLegacyIsStillUsed() = runTest {
        configureHarmonyDisabled()
        createTestee()

        testee.updateKey(KEY_NAME, TEST_VALUE)

        verify(legacyEditor).putString(eq(KEY_NAME), any())
        verify(legacyEditor).commit()
    }

    @Test
    fun whenKeyWrittenInMultiProcessModeThenReadableAfterFeatureDisabled() = runTest {
        // Simulate rollback scenario:
        // 1. Key written while multi-process enabled (writes to both harmony and legacy)
        // 2. Feature disabled, new instance created
        // 3. Key should be readable from legacy

        // Step 1: Write key in multi-process mode
        configureMultiProcessMode()
        whenever(harmonyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(null)
        createTestee()
        testee.updateKey(KEY_NAME, TEST_VALUE)

        // Verify key was written to legacy (for rollback support)
        verify(legacyEditor).putString(eq(KEY_NAME), eq(TEST_VALUE.toByteString().base64()))

        // Step 2: Disable feature and create new instance
        // Simulate legacy now has the key (written in step 1)
        whenever(legacyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(TEST_VALUE.toByteString().base64())
        configureHarmonyDisabled()
        createTestee()

        // Step 3: Read key - should come from legacy
        val result = testee.getKey(KEY_NAME)

        assertArrayEquals(TEST_VALUE, result)
    }

    // endregion

    // region Helper methods

    private fun configureHarmonyDisabled() {
        autofillFeature.useHarmony().setRawStoredState(State(enable = false))
        autofillFeature.readFromHarmony().setRawStoredState(State(enable = false))
        autofillServiceFeature.self().setRawStoredState(State(enable = false))
    }

    private fun configureHarmonyEnabled(harmonyPrefsReturnsNull: Boolean = false) {
        autofillFeature.useHarmony().setRawStoredState(State(enable = true))
        autofillFeature.readFromHarmony().setRawStoredState(State(enable = false))
        autofillServiceFeature.self().setRawStoredState(State(enable = false))
        sharedPreferencesProvider.stub {
            onBlocking { getMigratedEncryptedSharedPreferences(any(), any()) } doReturn if (harmonyPrefsReturnsNull) null else harmonyPrefs
        }
    }

    private fun configureReadFromHarmonyEnabled(harmonyPrefsReturnsNull: Boolean = false) {
        autofillFeature.useHarmony().setRawStoredState(State(enable = true))
        autofillFeature.readFromHarmony().setRawStoredState(State(enable = true))
        autofillServiceFeature.self().setRawStoredState(State(enable = false))
        sharedPreferencesProvider.stub {
            onBlocking { getMigratedEncryptedSharedPreferences(any(), any()) } doReturn if (harmonyPrefsReturnsNull) null else harmonyPrefs
        }
    }

    private fun configureMultiProcessMode(harmonyPrefsReturnsNull: Boolean = false) {
        autofillServiceFeature.self().setRawStoredState(State(enable = true))
        sharedPreferencesProvider.stub {
            onBlocking { getMigratedEncryptedSharedPreferences(any(), any()) } doReturn if (harmonyPrefsReturnsNull) null else harmonyPrefs
        }
    }

    @Test
    fun whenMultiProcessLatchedAndFlagDisabledMidSessionThenKeyExistsCheckStillUsesHarmony() = runTest {
        // Start in multi-process mode
        configureMultiProcessMode()
        whenever(harmonyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(null)
        createTestee()

        // First operation latches multiProcess=true
        testee.updateKey(KEY_NAME, TEST_VALUE)

        // Disable the flag mid-session
        autofillServiceFeature.self().setRawStoredState(State(enable = false))

        // Key now exists in harmony
        whenever(harmonyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(TEST_VALUE.toByteString().base64())

        // Try to write again - should be blocked because latched multiProcess still checks Harmony
        var exceptionThrown = false
        try {
            testee.updateKey(KEY_NAME, DIFFERENT_VALUE)
        } catch (e: SecureStorageException.KeyAlreadyExistsException) {
            exceptionThrown = true
        }

        assertTrue("Write should be blocked because latched multiProcess still checks Harmony", exceptionThrown)
    }

    @Test
    fun whenMultiProcessLatchedAndFlagDisabledMidSessionThenGetKeyStillReadsFromHarmony() = runTest {
        // Start in multi-process mode
        configureMultiProcessMode()
        whenever(harmonyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(TEST_VALUE.toByteString().base64())
        createTestee()

        // First operation latches multiProcess=true
        val firstRead = testee.getKey(KEY_NAME)
        assertArrayEquals(TEST_VALUE, firstRead)

        // Disable the flag mid-session
        autofillServiceFeature.self().setRawStoredState(State(enable = false))

        // Legacy has different/no value (simulating stale cache from another process writing)
        whenever(legacyPrefs.getString(eq(KEY_NAME), anyOrNull())).thenReturn(null)

        // Read again - should still read from Harmony because multiProcess is latched
        val secondRead = testee.getKey(KEY_NAME)

        assertArrayEquals("Should still read from Harmony due to latched multiProcess", TEST_VALUE, secondRead)
        // Verify we never touched legacy
        verify(legacyPrefs, never()).getString(any(), anyOrNull())
    }

    // endregion

    companion object {
        private const val KEY_NAME = "TEST_KEY"
        private val TEST_VALUE = "test_value".toByteArray()
        private val EXISTING_VALUE = "existing_value".toByteArray()
        private val DIFFERENT_VALUE = "different_value".toByteArray()
        private const val INVALID_BASE64 = "!!!not-valid-base64!!!"
    }

    /**
     * Fake factory that returns the provided SharedPreferences or throws an exception
     */
    private class FakeEncryptedPreferencesFactory(
        private val prefs: SharedPreferences? = null,
        private val exception: Exception? = null,
    ) : EncryptedPreferencesFactory {
        override fun create(filename: String): SharedPreferences {
            if (exception != null) throw exception
            return prefs ?: throw RuntimeException("No preferences configured")
        }
    }
}
