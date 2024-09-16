package com.duckduckgo.app.autocomplete.api

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealAutoCompleteScorerTest {

    private val testee = RealAutoCompleteScorer()

    @Test
    fun whenURLMatchesWithQueryThenScoreIsIncreased() {
        val query = "testcase.com/no"
        val score = testee.score(
            "Test case website",
            "https://www.testcase.com/notroot".toUri(),
            100,
            query,
        )

        Assert.assertTrue(score > 0)
    }

    @Test
    fun whenTitleMatchesFromTheBeginningThenScoreIsIncreased() {
        val query = "test"
        val score1 = testee.score(
            "Test case website",
            "https://www.website.com".toUri(),
            100,
            query,
        )

        val score2 = testee.score(
            "Case test website 2",
            "https://www.website2.com".toUri(),
            100,
            query,
        )

        Assert.assertTrue(score1 > score2)
    }

    @Test
    fun whenDomainMatchesFromTheBeginningThenScoreIsIncreased() {
        val query = "test"
        val score1 = testee.score(
            "Website",
            "https://www.test.com".toUri(),
            100,
            query,
        )

        val score2 = testee.score(
            "Website 2",
            "https://www.websitetest.com".toUri(),
            100,
            query,
        )

        Assert.assertTrue(score1 > score2)
    }

    @Test
    fun whenThereIsMoreVisitCountThenScoreIsIncreased() {
        val query = "website"
        val score1 = testee.score(
            "Website",
            "https://www.website.com".toUri(),
            100,
            query,
        )

        val score2 = testee.score(
            "Website 2",
            "https://www.website2.com".toUri(),
            101,
            query,
        )

        Assert.assertTrue(score1 < score2)
    }
}
