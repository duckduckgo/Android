package com.duckduckgo.mobile.android.duckduckgo.ui.bookmarks;

import android.support.annotation.NonNull;

import com.duckduckgo.mobile.android.duckduckgo.domain.bookmark.Bookmark;
import com.duckduckgo.mobile.android.duckduckgo.domain.bookmark.BookmarkRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 *    Copyright 2017 DuckDuckGo
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

public class BookmarksPresenterImpl implements BookmarksPresenter {

    private BookmarkRepository bookmarkRepository;

    private BookmarksView bookmarksView;

    private List<BookmarkEntity> bookmarks = new ArrayList<>();
    private boolean isEditing = false;

    public BookmarksPresenterImpl(BookmarkRepository bookmarkRepository) {
        this.bookmarkRepository = bookmarkRepository;
    }

    @Override
    public void restore(boolean isEditing) {
        this.isEditing = isEditing;
    }

    @Override
    public void attachView(BookmarksView bookmarksView) {
        this.bookmarksView = bookmarksView;
    }

    @Override
    public void detachView() {
        bookmarksView = null;
    }

    @Override
    public void load() {
        if (isEditing) {
            edit();
        }
        bookmarks.clear();
        for (Bookmark bookmark : bookmarkRepository.getAll()) {
            bookmarks.add(new BookmarkEntity(bookmark));
        }
        Collections.sort(bookmarks, new Comparator<BookmarkEntity>() {
            @Override
            public int compare(BookmarkEntity o1, BookmarkEntity o2) {
                return o1.getIndex() - o2.getIndex();
            }
        });
        loadBookmarks(bookmarks);
    }

    @Override
    public void edit() {
        isEditing = true;
        bookmarksView.showEditMode();
    }

    @Override
    public void dismiss() {
        if (isEditing && !bookmarks.isEmpty()) {
            isEditing = false;
            bookmarksView.dismissEditMode();
        } else {
            bookmarksView.close();
        }
    }

    @Override
    public void bookmarkSelected(int position) {
        BookmarkEntity bookmarkEntity = bookmarks.get(position);
        if (isEditing) {
            bookmarksView.showEditBookmark(new BookmarkEntity(bookmarkEntity));
        } else {
            bookmarksView.resultOpenBookmark(bookmarkEntity);
        }
    }

    @Override
    public void bookmarkDeleted(int position) {
        BookmarkEntity deletedBookmark = bookmarks.get(position);

        bookmarkRepository.delete(deletedBookmark);
        bookmarks.remove(position);

        for (BookmarkEntity bookmarkEntity : bookmarks) {
            int currentIndex = bookmarkEntity.getIndex();
            if (currentIndex > position) {
                bookmarkEntity.setIndex(currentIndex - 1);
                bookmarkRepository.update(bookmarkEntity);
            }
        }
        load();
    }

    @Override
    public void bookmarksMoved(int fromPosition, int toPosition) {
        BookmarkEntity from = bookmarks.get(fromPosition);
        from.setIndex(toPosition);

        BookmarkEntity to = bookmarks.get(toPosition);
        to.setIndex(fromPosition);

        bookmarkRepository.update(from);
        bookmarkRepository.update(to);

        load();
    }

    @Override
    public void saveEditedBookmark(@NonNull BookmarkEntity bookmarkEntity) {
        bookmarkRepository.update(bookmarkEntity);
        load();
    }

    private void loadBookmarks(List<BookmarkEntity> bookmarks) {
        setBookmarksVisibility();
        bookmarksView.loadBookmarks(bookmarks);
    }

    private void setBookmarksVisibility() {
        bookmarksView.showEmpty(bookmarks.isEmpty());
        setEditVisibility();
    }

    private void setEditVisibility() {
        bookmarksView.showEditButton(!isEditing && !bookmarks.isEmpty());
    }
}
