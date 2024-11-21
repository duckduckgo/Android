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
        assertEquals(NONE, testee.bucketNumberOfCredentials(0))
    }

    @Test
    fun whenBetweenOneAndThreeSavedPasswordThenBucketIsFew() {
        assertEquals(FEW, testee.bucketNumberOfCredentials(1))
        assertEquals(FEW, testee.bucketNumberOfCredentials(2))
        assertEquals(FEW, testee.bucketNumberOfCredentials(3))
    }

    @Test
    fun whenBetweenFourAndTenSavedPasswordThenBucketIsSome() {
        assertEquals(SOME, testee.bucketNumberOfCredentials(4))
        assertEquals(SOME, testee.bucketNumberOfCredentials(10))
    }

    @Test
    fun whenBetweenElevenAndFortyNineSavedPasswordThenBucketIsMany() {
        assertEquals(MANY, testee.bucketNumberOfCredentials(11))
        assertEquals(MANY, testee.bucketNumberOfCredentials(49))
    }

    @Test
    fun whenFiftyOrOverThenBucketIsMany() {
        assertEquals(LOTS, testee.bucketNumberOfCredentials(50))
        assertEquals(LOTS, testee.bucketNumberOfCredentials(Int.MAX_VALUE))
    }
}
