package com.duckduckgo.app.featureusage.di

import android.content.Context
import com.duckduckgo.app.featureusage.FeatureSegmentManagerImpl
import com.duckduckgo.app.featureusage.FeatureSegmentsDataStore
import com.duckduckgo.app.featureusage.FeatureSegmentsDataStoreSharedPreferences
import com.duckduckgo.app.featureusage.FeatureSegmentsManager
import dagger.Module
import dagger.Provides

@Module
class FeatureSegmentsModule {

    @Provides
    fun providesFeatureSegmentsDataStore(
        context: Context,
    ): FeatureSegmentsDataStore {
        return FeatureSegmentsDataStoreSharedPreferences(context)
    }

    @Provides
    fun providesFeatureSegmentsManager(
        featureSegmentsDataStore: FeatureSegmentsDataStore,
    ): FeatureSegmentsManager {
        return FeatureSegmentManagerImpl(featureSegmentsDataStore)
    }
}
