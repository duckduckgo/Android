package com.duckduckgo.autofill.impl.engagement.store

import com.duckduckgo.autofill.impl.engagement.store.AutofillEngagementBucketing.Companion.FEW
import com.duckduckgo.autofill.impl.engagement.store.AutofillEngagementBucketing.Companion.LOTS
import com.duckduckgo.autofill.impl.engagement.store.AutofillEngagementBucketing.Companion.MANY
import com.duckduckgo.autofill.impl.engagement.store.AutofillEngagementBucketing.Companion.NONE
import com.duckduckgo.autofill.impl.engagement.store.AutofillEngagementBucketing.Companion.SOME
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultAutofillEngagementBucketingTest {
    private val testee = DefaultAutofillEngagementBucketing()

    @Test
    fun whenZeroSavedPasswordsThenBucketIsNone() {
        assertEquals(NONE, testee.bucketNumberOfSavedPasswords(0))
    }

    @Test
    fun whenBetweenOneAndThreeSavedPasswordThenBucketIsFew() {
        assertEquals(FEW, testee.bucketNumberOfSavedPasswords(1))
        assertEquals(FEW, testee.bucketNumberOfSavedPasswords(2))
        assertEquals(FEW, testee.bucketNumberOfSavedPasswords(3))
    }

    @Test
    fun whenBetweenFourAndTenSavedPasswordThenBucketIsSome() {
        assertEquals(SOME, testee.bucketNumberOfSavedPasswords(4))
        assertEquals(SOME, testee.bucketNumberOfSavedPasswords(10))
    }

    @Test
    fun whenBetweenElevenAndFortyNineSavedPasswordThenBucketIsMany() {
        assertEquals(MANY, testee.bucketNumberOfSavedPasswords(11))
        assertEquals(MANY, testee.bucketNumberOfSavedPasswords(49))
    }

    @Test
    fun whenFiftyOrOverThenBucketIsMany() {
        assertEquals(LOTS, testee.bucketNumberOfSavedPasswords(50))
        assertEquals(LOTS, testee.bucketNumberOfSavedPasswords(Int.MAX_VALUE))
    }
}
