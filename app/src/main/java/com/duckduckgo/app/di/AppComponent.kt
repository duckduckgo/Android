/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.di

import android.app.Application
import com.duckduckgo.app.browser.autocomplete.BrowserAutoCompleteModule
import com.duckduckgo.app.browser.certificates.CertificateTrustedStoreModule
import com.duckduckgo.app.browser.di.BrowserModule
import com.duckduckgo.app.browser.favicon.FaviconModule
import com.duckduckgo.app.browser.rating.di.RatingModule
import com.duckduckgo.app.email.di.EmailModule
import com.duckduckgo.app.global.DuckDuckGoApplication
import com.duckduckgo.app.global.exception.UncaughtExceptionModule
import com.duckduckgo.app.onboarding.di.OnboardingModule
import com.duckduckgo.app.onboarding.di.WelcomePageModule
import com.duckduckgo.app.surrogates.di.ResourceSurrogateModule
import com.duckduckgo.app.trackerdetection.di.TrackerDetectionModule
import com.duckduckgo.app.usage.di.AppUsageModule
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.widget.EmptyFavoritesWidgetService
import com.duckduckgo.widget.FavoritesWidgetService
import com.duckduckgo.widget.SearchAndFavoritesWidget
import com.duckduckgo.widget.SearchWidget
import com.squareup.anvil.annotations.MergeComponent
import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjector
import dagger.android.support.AndroidSupportInjectionModule
import kotlinx.coroutines.CoroutineScope
import retrofit2.Retrofit
import javax.inject.Named
import javax.inject.Singleton

@Singleton
@MergeComponent(
    scope = AppObjectGraph::class,
    modules = [
        ApplicationModule::class,
        WorkerModule::class,
        AndroidSupportInjectionModule::class,
        NetworkModule::class,
        AppConfigurationDownloaderModule::class,
        StatisticsModule::class,
        StoreModule::class,
        DatabaseModule::class,
        DaoModule::class,
        JsonModule::class,
        SystemComponentsModule::class,
        BrowserModule::class,
        BrowserAutoCompleteModule::class,
        ResourceSurrogateModule::class,
        TrackerDetectionModule::class,
        NotificationModule::class,
        OnboardingModule::class,
        VariantModule::class,
        FaviconModule::class,
        PrivacyModule::class,
        WidgetModule::class,
        RatingModule::class,
        AppUsageModule::class,
        FileModule::class,
        UncaughtExceptionModule::class,
        CoroutinesModule::class,
        CertificateTrustedStoreModule::class,
        WelcomePageModule::class,
        EmailModule::class
    ]
)
interface AppComponent : AndroidInjector<DuckDuckGoApplication> {

    @Component.Builder
    interface Builder {

        @BindsInstance
        fun application(application: Application): Builder

        @BindsInstance
        fun applicationCoroutineScope(@AppCoroutineScope applicationCoroutineScope: CoroutineScope): Builder

        fun build(): AppComponent
    }

    fun inject(searchWidget: SearchWidget)

    fun inject(searchAndFavsWidget: SearchAndFavoritesWidget)

    fun inject(favoritesWidgetItemFactory: FavoritesWidgetService.FavoritesWidgetItemFactory)

    fun inject(emptyFavoritesWidgetItemFactory: EmptyFavoritesWidgetService.EmptyFavoritesWidgetItemFactory)

    // accessor to Retrofit instance for test only only for test
    @Named("api")
    fun retrofit(): Retrofit
}
