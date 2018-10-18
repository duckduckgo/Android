/*
 * Copyright (c) 2018 DuckDuckGo
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

import com.duckduckgo.app.entities.EntityMapping
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.privacy.model.PrivacyPracticesImpl
import com.duckduckgo.app.privacy.store.TermsOfServiceStore
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class PrivacyModule {

    @Provides
    @Singleton
    fun privacyPractices(termsOfServiceStore: TermsOfServiceStore, entityMapping: EntityMapping): PrivacyPractices
            = PrivacyPracticesImpl(termsOfServiceStore, entityMapping)

}