package com.duckduckgo.mobile.android.duckduckgo.ui.bookmarks;

import android.support.annotation.NonNull;

import com.duckduckgo.mobile.android.duckduckgo.domain.bookmark.Bookmark;
import com.duckduckgo.mobile.android.duckduckgo.domain.bookmark.BookmarkRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by fgei on 6/12/17.
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
