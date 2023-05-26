package com.duckduckgo.app.featureusage.di

import android.content.Context
import com.duckduckgo.app.featureusage.FeatureSegmentManagerImpl
import com.duckduckgo.app.featureusage.FeatureSegmentsManager
import com.duckduckgo.app.featureusage.db.FeatureSegmentsDataStore
import com.duckduckgo.app.featureusage.db.FeatureSegmentsDataStoreSharedPreferences
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.browser.api.UserBrowserProperties
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
        pixel: Pixel,
        userBrowserProperties: UserBrowserProperties,
    ): FeatureSegmentsManager {
        return FeatureSegmentManagerImpl(featureSegmentsDataStore, pixel, userBrowserProperties)
    }
}
