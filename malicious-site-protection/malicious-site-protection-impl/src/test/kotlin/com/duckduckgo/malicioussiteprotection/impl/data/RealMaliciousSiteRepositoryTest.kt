package com.duckduckgo.malicioussiteprotection.impl.data

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed.PHISHING
import com.duckduckgo.malicioussiteprotection.impl.MaliciousSitePixelName.MALICIOUS_SITE_CLIENT_TIMEOUT
import com.duckduckgo.malicioussiteprotection.impl.data.db.FilterEntity
import com.duckduckgo.malicioussiteprotection.impl.data.db.HashPrefixEntity
import com.duckduckgo.malicioussiteprotection.impl.data.db.MaliciousSiteDao
import com.duckduckgo.malicioussiteprotection.impl.data.db.RevisionEntity
import com.duckduckgo.malicioussiteprotection.impl.data.network.FilterSetResponse
import com.duckduckgo.malicioussiteprotection.impl.data.network.HashPrefixResponse
import com.duckduckgo.malicioussiteprotection.impl.data.network.MaliciousSiteDatasetService
import com.duckduckgo.malicioussiteprotection.impl.data.network.MaliciousSiteService
import com.duckduckgo.malicioussiteprotection.impl.data.network.MatchResponse
import com.duckduckgo.malicioussiteprotection.impl.data.network.MatchesResponse
import com.duckduckgo.malicioussiteprotection.impl.data.network.RevisionResponse
import com.duckduckgo.malicioussiteprotection.impl.models.Filter
import com.duckduckgo.malicioussiteprotection.impl.models.FilterSet
import com.duckduckgo.malicioussiteprotection.impl.models.FilterSetWithRevision.PhishingFilterSetWithRevision
import com.duckduckgo.malicioussiteprotection.impl.models.HashPrefixesWithRevision.PhishingHashPrefixesWithRevision
import com.duckduckgo.malicioussiteprotection.impl.models.Match
import com.duckduckgo.malicioussiteprotection.impl.models.MatchesResult
import com.duckduckgo.malicioussiteprotection.impl.models.MatchesResult.Ignored
import com.duckduckgo.malicioussiteprotection.impl.models.Type
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever

class RealMaliciousSiteRepositoryTest {

    @get:org.junit.Rule
    var coroutineRule = com.duckduckgo.common.test.CoroutineTestRule()

    private val maliciousSiteDao: MaliciousSiteDao = mock()
    private val maliciousSiteService: MaliciousSiteService = mock()
    private val maliciousSiteDatasetService: MaliciousSiteDatasetService = mock()
    private val mockPixel: Pixel = mock()
    private val repository = RealMaliciousSiteRepository(
        maliciousSiteDao,
        maliciousSiteService,
        maliciousSiteDatasetService,
        coroutineRule.testDispatcherProvider,
        mockPixel,
    )

    @Test
    fun loadFilters_updatesFiltersWhenNetworkRevisionIsHigher() = runTest {
        val networkRevision = 2
        val latestRevision = listOf(RevisionEntity(PHISHING.name, Type.FILTER_SET.name, 1))
        val phishingFilterSetResponse = FilterSetResponse(setOf(), setOf(), networkRevision, false)

        whenever(maliciousSiteService.getRevision()).thenReturn(RevisionResponse(networkRevision))
        whenever(maliciousSiteDao.getLatestRevision()).thenReturn(latestRevision)
        whenever(maliciousSiteDatasetService.getPhishingFilterSet(any())).thenReturn(phishingFilterSetResponse)

        repository.loadFilters(*enumValues<Feed>())

        verify(maliciousSiteDatasetService).getPhishingFilterSet(latestRevision.first().revision)
        verify(maliciousSiteDao).updateFilters(any<PhishingFilterSetWithRevision>())
    }

    @Test
    fun loadFilters_doesNotUpdateFiltersWhenNetworkRevisionIsNotHigher() = runTest {
        val networkRevision = 1
        val latestRevision = listOf(RevisionEntity(PHISHING.name, Type.FILTER_SET.name, 1))

        whenever(maliciousSiteService.getRevision()).thenReturn(RevisionResponse(networkRevision))
        whenever(maliciousSiteDao.getLatestRevision()).thenReturn(latestRevision)

        repository.loadFilters(*enumValues<Feed>())

        verify(maliciousSiteDatasetService, never()).getPhishingFilterSet(any())
        verify(maliciousSiteDao, never()).updateFilters(any())
    }

    @Test
    fun loadHashPrefixes_updatesHashPrefixesWhenNetworkRevisionIsHigher() = runTest {
        val networkRevision = 2
        val latestRevision = listOf(RevisionEntity(PHISHING.name, Type.HASH_PREFIXES.name, 1))
        val phishingHashPrefixResponse = HashPrefixResponse(setOf(), setOf(), networkRevision, false)

        whenever(maliciousSiteService.getRevision()).thenReturn(RevisionResponse(networkRevision))
        whenever(maliciousSiteDao.getLatestRevision()).thenReturn(latestRevision)
        whenever(maliciousSiteDatasetService.getPhishingHashPrefixes(any())).thenReturn(phishingHashPrefixResponse)

        repository.loadHashPrefixes(*enumValues<Feed>())

        verify(maliciousSiteDatasetService).getPhishingHashPrefixes(latestRevision.first().revision)
        verify(maliciousSiteDao).updateHashPrefixes(any<PhishingHashPrefixesWithRevision>())
    }

    @Test
    fun loadHashPrefixes_doesNotUpdateHashPrefixesWhenNetworkRevisionIsNotHigher() = runTest {
        val networkRevision = 1
        val latestRevision = listOf(RevisionEntity(PHISHING.name, Type.HASH_PREFIXES.name, 1))

        whenever(maliciousSiteService.getRevision()).thenReturn(RevisionResponse(networkRevision))
        whenever(maliciousSiteDao.getLatestRevision()).thenReturn(latestRevision)

        repository.loadHashPrefixes(*enumValues<Feed>())

        verify(maliciousSiteDatasetService, never()).getPhishingHashPrefixes(any())
        verify(maliciousSiteDao, never()).updateHashPrefixes(any())
    }

    @Test
    fun containsHashPrefix_returnsTrueWhenHashPrefixExists() = runTest {
        val hashPrefix = "testPrefix"

        whenever(maliciousSiteDao.getHashPrefix(hashPrefix)).thenReturn(HashPrefixEntity(hashPrefix, PHISHING.name))

        val result = repository.getFeedForHashPrefix(hashPrefix)

        assertEquals(PHISHING, result)
    }

    @Test
    fun getFilters_returnsFiltersWhenHashExists() = runTest {
        val hash = "testHash"
        val filters = FilterEntity(hash, "regex", PHISHING.name)

        whenever(maliciousSiteDao.getFilter(hash)).thenReturn(filters)

        val result = repository.getFilters(hash)
        val expected = FilterSet(Filter(filters.hash, filters.regex), PHISHING)

        assertTrue(result?.feed == expected.feed)
        assertEquals(result?.filters, expected.filters)
    }

    @Test
    fun matches_returnsMatchesWhenHashPrefixExists() = runTest {
        val hashPrefix = "testPrefix"
        val matchesResponse = MatchesResponse(
            listOf(
                MatchResponse("hostname", "url", "regex", "hash", PHISHING.name),
            ),
        )

        val expected = matchesResponse.matches.map { Match(it.hostname, it.url, it.regex, it.hash, PHISHING) }.let { matches ->
            MatchesResult.Result(matches)
        }

        whenever(maliciousSiteService.getMatches(hashPrefix)).thenReturn(matchesResponse)

        val result = repository.matches(hashPrefix)

        assertEquals(expected, result)
    }

    @Test
    fun matches_returnsIgnoredOnTimeout() = runTest {
        val hashPrefix = "testPrefix"

        whenever(maliciousSiteService.getMatches(hashPrefix)).thenThrow(TimeoutCancellationException::class.java)

        val result = repository.matches(hashPrefix)

        assertTrue(result is Ignored)
        verify(mockPixel).fire(MALICIOUS_SITE_CLIENT_TIMEOUT)
    }
}
