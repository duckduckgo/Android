package com.duckduckgo.httpsupgrade.store

import android.content.Context
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.store.BinaryDataStore
import com.duckduckgo.app.httpsupgrade.model.HttpsBloomFilterSpec
import com.duckduckgo.app.httpsupgrade.model.HttpsFalsePositiveDomain
import com.duckduckgo.app.httpsupgrade.store.HttpsBloomFilterSpecDao
import com.duckduckgo.app.httpsupgrade.store.HttpsDataPersister
import com.duckduckgo.app.httpsupgrade.store.HttpsEmbeddedDataPersister
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import timber.log.Timber

class PlayHttpsEmbeddedDataPersister(
    private val httpsDataPersister: HttpsDataPersister,
    private val binaryDataStore: BinaryDataStore,
    private val httpsBloomSpecDao: HttpsBloomFilterSpecDao,
    private val context: Context,
    private val moshi: Moshi
) : HttpsEmbeddedDataPersister {

    override fun shouldPersistEmbeddedData(): Boolean {
        val specification = httpsBloomSpecDao.get() ?: return true
        return !binaryDataStore.verifyCheckSum(HttpsBloomFilterSpec.HTTPS_BINARY_FILE, specification.sha256)
    }

    override fun persistEmbeddedData() {
        Timber.d("Updating https data from embedded files")
        val specJson = context.resources.openRawResource(R.raw.https_mobile_v2_bloom_spec).bufferedReader().use { it.readText() }
        val specAdapter = moshi.adapter(HttpsBloomFilterSpec::class.java)

        val falsePositivesJson = context.resources.openRawResource(R.raw.https_mobile_v2_false_positives).bufferedReader().use { it.readText() }
        val falsePositivesType = Types.newParameterizedType(List::class.java, HttpsFalsePositiveDomain::class.java)
        val falsePositivesAdapter: JsonAdapter<List<HttpsFalsePositiveDomain>> = moshi.adapter(falsePositivesType)

        val bytes = context.resources.openRawResource(R.raw.https_mobile_v2_bloom).use { it.readBytes() }
        httpsDataPersister.persistBloomFilter(specAdapter.fromJson(specJson)!!, bytes, falsePositivesAdapter.fromJson(falsePositivesJson)!!)
    }
}
