/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.browser.certificates.blocklist

import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

// The rejections registry is created before the DI graph exists (the provider must be installed before any TLS use),
// so the graph exposes that same instance rather than owning one.
@BindingContainer
@ContributesTo(AppScope::class)
object CertificateBlocklistModule {
    @Provides
    fun certificateBlocklistRejections(): CertificateBlocklistRejections = CertificateBlocklistInstaller.rejections
}
