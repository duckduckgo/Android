/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.bookmarks.ui

import androidx.recyclerview.widget.DiffUtil

class BookmarksDiffCallback : DiffUtil.ItemCallback<BookmarksAdapter.BookmarksItemTypes>() {
    override fun areItemsTheSame(oldItem: BookmarksAdapter.BookmarksItemTypes, newItem: BookmarksAdapter.BookmarksItemTypes): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: BookmarksAdapter.BookmarksItemTypes, newItem: BookmarksAdapter.BookmarksItemTypes): Boolean {
        return oldItem == newItem
    }
}

class FavoritesDiffCallback : DiffUtil.ItemCallback<FavoritesAdapter.FavoriteItemTypes>() {
    override fun areItemsTheSame(oldItem: FavoritesAdapter.FavoriteItemTypes, newItem: FavoritesAdapter.FavoriteItemTypes): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: FavoritesAdapter.FavoriteItemTypes, newItem: FavoritesAdapter.FavoriteItemTypes): Boolean {
        return oldItem == newItem
    }
}