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

package com.duckduckgo.sync.impl.exchange.v2

/**
 * Whether this device displayed the QR code (Presenter) or scanned the peer's QR code
 * (Scanner). One of the inputs to automatic role election in [ExchangeV2Runner].
 *
 * Spec: Unified Algorithm "Exchange Introduction v2 - host/joiner decision".
 */
enum class PairingRole { Presenter, Scanner }
