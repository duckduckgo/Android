/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.wireguard.crypto

infix fun Byte.and(that: Int): Int = this.toInt().and(that)

infix fun Byte.or(that: Int): Int = this.toInt().or(that)

infix fun Byte.shr(that: Int): Int = this.toInt().shr(that)

infix fun Byte.shl(that: Int): Int = this.toInt().shl(that)

infix fun Byte.ushr(that: Int): Int = this.toInt().ushr(that)
