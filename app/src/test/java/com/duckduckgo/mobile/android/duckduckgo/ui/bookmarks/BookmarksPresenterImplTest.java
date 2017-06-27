package com.duckduckgo.mobile.android.duckduckgo.ui.bookmarks;

import com.duckduckgo.mobile.android.duckduckgo.domain.bookmark.Bookmark;
import com.duckduckgo.mobile.android.duckduckgo.domain.bookmark.BookmarkRepository;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

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
public class BookmarksPresenterImplTest {

    private BookmarkRepository mockBookmarkRepository;
    private BookmarksView mockbookmarksView;
    private BookmarksPresenter bookmarksPresenter;

    private List<Bookmark> mockBookmarks;

    private String[] urls = {"https://duckduckgo.com", "https://bing.com", "https://google.com", "https://test.com"};
    private String[] names = {"DuckDuckGo", "Bing", "Google", "Test"};

    @Before
    public void setup() {
        mockBookmarkRepository = mock(BookmarkRepository.class);
        mockbookmarksView = mock(BookmarksView.class);

        initBookmarks();

        bookmarksPresenter = new BookmarksPresenterImpl(mockBookmarkRepository);
        bookmarksPresenter.attachView(mockbookmarksView);
    }

    private void initBookmarks() {
        mockBookmarks = new ArrayList<>();
        for (int i = 0; i < urls.length; i++) {
            BookmarkEntity bookmarkEntity = BookmarkEntity.create();
            bookmarkEntity.setIndex(i);
            bookmarkEntity.setUrl(urls[i]);
            bookmarkEntity.setName(names[i]);
            mockBookmarks.add(bookmarkEntity);
        }
    }

    private BookmarkEntity createBookmarkModel() {
        BookmarkEntity out = BookmarkEntity.create();
        out.setName(names[0]);
        out.setUrl(urls[0]);
        out.setIndex(1);
        return out;
    }

    private void loadData() {
        when(mockBookmarkRepository.getAll()).thenReturn(mockBookmarks);
        bookmarksPresenter.load();
    }

    private void loadEmptyData() {
        when(mockBookmarkRepository.getAll()).thenReturn(new ArrayList<Bookmark>());
        bookmarksPresenter.load();
    }

    @Test
    public void whenDetachThenHaveNoInteractionWithView() {
        bookmarksPresenter.detachView();
        verifyZeroInteractions(mockbookmarksView);
    }

    @Test
    public void whenLoadValidListThenShowBookmarks() {
        loadData();
        verify(mockbookmarksView, times(1)).loadBookmarks(ArgumentMatchers.<BookmarkEntity>anyList());
    }

    @Test
    public void whenLoadValidListThenHideEmptyView() {
        loadData();
        verify(mockbookmarksView, times(1)).showEmpty(false);
    }

    @Test
    public void whenLoadValidListThenShowEditMenuItem() {
        loadData();
        verify(mockbookmarksView, times(1)).showEditButton(true);
    }

    @Test
    public void whenLoadEmptyListThenShowEmptyView() {
        loadEmptyData();
        verify(mockbookmarksView, times(1)).showEmpty(true);
    }

    @Test
    public void whenLoadEmptyListThenHideEditMenuItem() {
        loadEmptyData();
        verify(mockbookmarksView, times(1)).showEditButton(false);
    }

    @Test
    public void whenEditItemThenShowEditableList() {
        bookmarksPresenter.edit();
        verify(mockbookmarksView, times(1)).showEditMode();
    }

    @Test
    public void whenNotEditingAndDismissThenCloseView() {
        loadData();
        bookmarksPresenter.dismiss();
        verify(mockbookmarksView, times(1)).close();
    }

    @Test
    public void whenEditingAndDismissThenCloseEditMode() {
        loadData();
        bookmarksPresenter.edit();
        bookmarksPresenter.dismiss();
        verify(mockbookmarksView, times(1)).dismissEditMode();
    }

    @Test
    public void whenEditingAndSelectBookmarkThenRequestEditBookmark() {
        loadData();
        bookmarksPresenter.edit();
        bookmarksPresenter.bookmarkSelected(anyInt());
        verify(mockbookmarksView).showEditBookmark(any(BookmarkEntity.class));
    }

    @Test
    public void whenDeletingBookmarkThenDeleteBookmark() {
        loadData();
        bookmarksPresenter.edit();
        bookmarksPresenter.bookmarkDeleted(anyInt());
        verify(mockBookmarkRepository, times(1)).delete(any(BookmarkEntity.class));
    }

    @Test
    public void whenSwapBookmarksThenUpdateBookmarks() {
        final int fromPosition = 0;
        final int toPosition = 2;
        loadData();
        bookmarksPresenter.edit();
        bookmarksPresenter.bookmarksMoved(fromPosition, toPosition);
        verify(mockBookmarkRepository, times(2)).update(any(BookmarkEntity.class));
    }

    @Test
    public void whenNotEditingAndSelectBookmarkThenResultOpenBookmark() {
        loadData();
        bookmarksPresenter.bookmarkSelected(anyInt());
        verify(mockbookmarksView).resultOpenBookmark(any(BookmarkEntity.class));
    }

    @Test
    public void whenSaveEditedBookmarkThenUpdateBookmark() {
        BookmarkEntity editedBookmark = createBookmarkModel();
        loadData();
        bookmarksPresenter.saveEditedBookmark(editedBookmark);
        verify(mockBookmarkRepository, times(1)).update(editedBookmark);
    }

}