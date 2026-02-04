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

package com.duckduckgo.remote.messaging.impl.modal.cardslist

import com.duckduckgo.remote.messaging.api.CardItem
import com.duckduckgo.remote.messaging.api.Content

sealed class ModalListItem {
    abstract val id: String

    data class Header(
        override val id: String = MODAL_HEADER_ID,
        val titleText: String,
        val placeholder: Content.Placeholder,
        val imageUrl: String?,
        val imageFilePath: String?,
    ) : ModalListItem()

    data class CardListItem(
        override val id: String,
        val cardItem: CardItem,
    ) : ModalListItem()
}

private const val MODAL_HEADER_ID = "modal-header-id"
