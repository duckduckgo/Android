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

package com.duckduckgo.url

/**
 * Classifies what kind of host a [DaxDomain] represents. "Validity" is multi-valued — `localhost`
 * and IPs are perfectly valid hosts, just not registrable domains — so consumers branch on the
 * kind they accept (e.g. an allowlist takes only [REGISTRABLE_DOMAIN]) rather than a binary
 * valid/invalid. Open to extension without breaking callers.
 */
enum class DaxDomainType {
    /** A registrable domain (has an eTLD+1), e.g. `example.com`, `sub.example.co.uk`. */
    REGISTRABLE_DOMAIN,

    /** An IP-literal host, e.g. `127.0.0.1`. */
    IP,

    /** The `localhost` host. */
    LOCALHOST,

    /** Anything else with no registrable domain (a bare public suffix, a single-label host, …). */
    OTHER,
}
