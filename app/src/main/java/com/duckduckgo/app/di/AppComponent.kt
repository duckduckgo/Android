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
import com.duckduckgo.app.global.DuckDuckGoApplication
import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjector
import dagger.android.support.AndroidSupportInjectionModule
import javax.inject.Singleton

@Singleton
@Component(modules = [
    (ApplicationModule::class),
    (JobsModule::class),
    (AndroidBindingModule::class),
    (AndroidSupportInjectionModule::class),
    (NetworkModule::class),
    (StoreModule::class),
    (PrivacyModule::class),
    (DatabaseModule::class),
    (JsonModule::class),
    (StringModule::class)
])
interface AppComponent : AndroidInjector<DuckDuckGoApplication> {

    @Component.Builder
    abstract class Builder : AndroidInjector.Builder<DuckDuckGoApplication>() {

        @BindsInstance
        abstract fun application(application: Application): AppComponent.Builder
    }
}