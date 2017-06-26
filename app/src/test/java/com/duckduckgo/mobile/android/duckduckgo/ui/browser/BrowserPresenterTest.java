package com.duckduckgo.mobile.android.duckduckgo.ui.browser;

import com.duckduckgo.mobile.android.duckduckgo.domain.bookmark.BookmarkRepository;
import com.duckduckgo.mobile.android.duckduckgo.domain.tab.Tab;
import com.duckduckgo.mobile.android.duckduckgo.domain.tab.TabRepository;
import com.duckduckgo.mobile.android.duckduckgo.ui.bookmarks.BookmarkEntity;
import com.duckduckgo.mobile.android.duckduckgo.ui.main.MainView;
import com.duckduckgo.mobile.android.duckduckgo.ui.navigationbar.NavigationBarView;
import com.duckduckgo.mobile.android.duckduckgo.ui.omnibar.OmnibarView;
import com.duckduckgo.mobile.android.duckduckgo.ui.tab.TabEntity;
import com.duckduckgo.mobile.android.duckduckgo.ui.tab.TabView;
import com.duckduckgo.mobile.android.duckduckgo.ui.tabswitcher.TabSwitcherView;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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

public class BrowserPresenterTest {

    private MainView mockMainView;
    private BrowserView mockBrowserView;
    private OmnibarView mockOmnibarView;
    private NavigationBarView mockNavigationBarView;
    private TabView mockTabView;
    private TabSwitcherView mockTabSwitcherView;
    private TabRepository mockTabRepository;
    private BookmarkRepository mockBookmarkRepository;

    private BrowserPresenter browserPresenter;

    private List<Tab> mockTabs = new ArrayList<>();

    @Before
    public void setup() {
        mockMainView = mock(MainView.class);
        mockBrowserView = mock(BrowserView.class);
        mockOmnibarView = mock(OmnibarView.class);
        mockNavigationBarView = mock(NavigationBarView.class);
        mockTabView = mock(TabView.class);
        mockTabSwitcherView = mock(TabSwitcherView.class);
        mockTabRepository = mock(TabRepository.class);
        mockBookmarkRepository = mock(BookmarkRepository.class);

        initTabs();

        browserPresenter = new BrowserPresenterImpl(mockTabRepository, mockBookmarkRepository);
        browserPresenter.attachBrowserView(mockBrowserView);
        browserPresenter.attachOmnibarView(mockOmnibarView);
        browserPresenter.attachNavigationBarView(mockNavigationBarView);
        browserPresenter.attachMainview(mockMainView);
    }

    private void restoreSession() {
        when(mockTabRepository.getAll()).thenReturn(mockTabs);
        browserPresenter.loadTabs(true);
    }

    private void restoreEmptySession() {
        when(mockTabRepository.getAll()).thenReturn(new ArrayList<Tab>());
        browserPresenter.loadTabs(true);
    }

    private void startSession() {
        when(mockTabRepository.getAll()).thenReturn(mockTabs);
        browserPresenter.loadTabs(false);
    }

    private void initTabs() {
        mockTabs.clear();
        for (int i = 0; i < urls.length; i++) {
            String url = urls[i];
            String title = titles[i];
            TabEntity tab = TabEntity.create();
            tab.setTitle(title);
            tab.setCurrentUrl(url);
            tab.setCanGoBack(false);
            tab.setCanGoForward(false);
            mockTabs.add(tab);
        }
    }

    private BookmarkEntity createMockBookmark() {
        BookmarkEntity bookmark = BookmarkEntity.create();
        bookmark.setIndex(0);
        bookmark.setName("Test");
        bookmark.setUrl("https://test.com");
        return bookmark;
    }

    private static String[] urls = {"https://duckduckgo.com/?q=test", "https://test.com", "https//duckduckgo.com"};
    private static String[] titles = {"test at DuckduckGo", "Test", "DuckDuckgo"};

    @Test
    public void whenDetachBrowserViewThenHaveNoInteractionWithBrowserView() {
        browserPresenter.detachBrowserView();
        verifyZeroInteractions(mockBrowserView);
    }

    @Test
    public void whenDetachMainViewThenHaveNoInteractionWithMainView() {
        browserPresenter.detachMainView();
        verifyZeroInteractions(mockMainView);
    }

    @Test
    public void whenDetachOmnibarViewThenHaveNoInteractionWithOmnibarView() {
        browserPresenter.detachOmnibarView();
        verifyZeroInteractions(mockOmnibarView);
    }

    @Test
    public void whenLoadTabsWithTabsSavedAndRestoreIsFalseThenClearAllRestoredTabs() {
        startSession();
        verify(mockTabRepository).deleteAll();
    }

    @Test
    public void whenLoadTabsWithRestoreIsFalseThenSaveSessionWithOnlyNewTab() {
        final int newTabCreate = 1;
        startSession();
        browserPresenter.saveSession();
        verify(mockTabRepository, times(newTabCreate)).insert(any(Tab.class));
    }

    @Test
    public void whenSaveSessionThenInsertAllTabs() {
        restoreSession();
        browserPresenter.saveSession();
        verify(mockTabRepository, times(mockTabs.size())).insert(any(Tab.class));
    }

    @Test
    public void whenCreateNewTabThenCreateNewTabInBrowserAndShowsIt() {
        restoreSession();
        browserPresenter.openNewTab();
        verify(mockBrowserView, times(1)).createNewTab(anyString());
        verify(mockBrowserView, times(2)).showTab(anyString());
    }

    @Test
    public void whenCreateNewTabThenResetOmnibarText() {
        restoreSession();
        browserPresenter.openNewTab();
        verify(mockOmnibarView, times(2)).clearText();
        verify(mockOmnibarView, times(2)).clearFocus();
        verify(mockOmnibarView, times(2)).setRefreshEnabled(false);
    }

    @Test
    public void whenCreateNewTabThenResetNavigationBar() {
        restoreSession();
        verify(mockNavigationBarView, times(2)).setBackEnabled(anyBoolean());
        verify(mockNavigationBarView, times(2)).setForwardEnabled(anyBoolean());
        browserPresenter.openNewTab();
        verify(mockNavigationBarView, times(4)).setBackEnabled(false);
        verify(mockNavigationBarView, times(4)).setForwardEnabled(false);
    }

    @Test
    public void whenOpenTabThenHaveNoInteractionWithTabSwitcher() {
        restoreSession();
        browserPresenter.openTab(0);
        verifyZeroInteractions(mockTabSwitcherView);
    }

    @Test
    public void whenOpenTabThenBrowserShowTheRightView() {
        final int position = 1;
        final String tabId = mockTabs.get(position).getId();
        restoreSession();
        browserPresenter.openTab(position);
        verify(mockBrowserView, times(1)).showTab(tabId);
    }

    @Test
    public void whenOpenTabThenOmnibarUpdateCurrentUrl() {
        final int position = 1;
        final String url = mockTabs.get(position).getCurrentUrl();
        restoreSession();
        browserPresenter.openTab(position);
        verify(mockOmnibarView, times(1)).displayText(url);
    }

    @Test
    public void whenOpenTabThenUpdateNavigationBarBackAndForwardButtons() {
        final int position = 1;
        final boolean canGoBack = mockTabs.get(position).canGoBack();
        final boolean canGoForward = mockTabs.get(position).canGoForward();
        restoreSession();
        //reset and set the currentTab
        verify(mockNavigationBarView, times(2)).setBackEnabled(canGoBack);
        verify(mockNavigationBarView, times(2)).setForwardEnabled(canGoForward);
        browserPresenter.openTab(position);
        //reset and set the new tab
        verify(mockNavigationBarView, times(4)).setBackEnabled(canGoBack);
        verify(mockNavigationBarView, times(4)).setForwardEnabled(canGoForward);
    }

    @Test
    public void whenCloseTabWithTabSwitcherOpenThenReloadTabSwitcherList() {
        final int closePosition = 1;
        restoreSession();
        browserPresenter.loadTabsSwitcherTabs();
        browserPresenter.attachTabSwitcherView(mockTabSwitcherView);
        browserPresenter.closeTab(closePosition);
        verify(mockTabSwitcherView, times(1)).showTabs(ArgumentMatchers.<TabEntity>anyList());
    }

    @Test
    public void whenCloseTabThenBrowserDeleteTab() {
        final int position = 1;
        final String tabId = mockTabs.get(position).getId();
        restoreSession();
        browserPresenter.closeTab(position);
        verify(mockBrowserView, times(1)).deleteTab(tabId);
    }

    @Test
    public void whenCloseTabWithIndexHigherThenCurrentTabThenDoNotChangeTab() {
        final int selectedPosition = 0;
        final int closePosition = 1;
        restoreSession();
        verify(mockBrowserView, times(1)).clearBrowser();
        verify(mockBrowserView, times(1)).showTab(anyString());
        browserPresenter.openTab(selectedPosition);
        verify(mockBrowserView, times(2)).showTab(anyString());
        browserPresenter.closeTab(closePosition);
        verify(mockBrowserView, times(1)).deleteTab(anyString());
        verifyNoMoreInteractions(mockBrowserView);
    }

    @Test
    public void whenCloseLastTabThenCreateNewTab() {
        final int selectedPosition = 0;
        restoreSession();
        verify(mockBrowserView, times(1)).clearBrowser();
        verify(mockBrowserView, times(1)).showTab(anyString());
        browserPresenter.openTab(selectedPosition);
        verify(mockBrowserView, times(2)).showTab(anyString());
        browserPresenter.closeTab(2);
        browserPresenter.closeTab(1);
        browserPresenter.closeTab(0);
        verify(mockBrowserView, times(3)).deleteTab(anyString());
        verify(mockBrowserView, times(1)).createNewTab(anyString());
    }

    @Test
    public void whenFireThenTabRepositoryDeleteAllTabs() {
        restoreSession();
        //delete stored tabs
        verify(mockTabRepository, times(1)).deleteAll();
        browserPresenter.fire();
        //delete current tabs
        verify(mockTabRepository, times(2)).deleteAll();
    }

    @Test
    public void whenFireThenBrowserDeleteAllTabsAndclearPrivacyData() {
        restoreSession();
        browserPresenter.fire();
        verify(mockBrowserView, times(1)).deleteAllTabs();
        verify(mockBrowserView, times(1)).deleteAllPrivacyData();
    }

    @Test
    public void whenOpenTabSwitcherThenNavigateToTabSwitcher() {
        browserPresenter.openTabSwitcher();
        verify(mockMainView, times(1)).navigateToTabSwitcher();
    }

    @Test
    public void whenLoadTabSwitcherThenLoadTabs() {
        restoreSession();
        browserPresenter.attachTabSwitcherView(mockTabSwitcherView);
        browserPresenter.loadTabsSwitcherTabs();
        verify(mockTabSwitcherView, times(1)).showTabs(ArgumentMatchers.<TabEntity>anyList());
    }

    @Test
    public void whenDismissTabSwitcherWithTabSwitcherAttachedThenCloseTabSwitcher() {
        restoreSession();
        browserPresenter.attachTabSwitcherView(mockTabSwitcherView);
        browserPresenter.dismissTabSwitcher();
        verify(mockMainView, times(1)).dismissTabSwitcher();
    }

    @Test
    public void whenDismissTabSwitcherWithTabSwitcherNotAttachedThenNoMainAndTabSwitcherInteraction() {
        browserPresenter.dismissTabSwitcher();
        verifyZeroInteractions(mockMainView);
        verifyZeroInteractions(mockTabSwitcherView);
    }

    @Test
    public void whenRequestSearchInCurrentTabThenBrowserDontChangeTabView() {
        browserPresenter.attachTabView(mockTabView);
        restoreSession();
        verify(mockBrowserView, times(1)).clearBrowser();
        verify(mockBrowserView, times(1)).showTab(anyString());
        browserPresenter.requestSearchInCurrentTab(anyString());
        verifyNoMoreInteractions(mockBrowserView);
    }

    @Test
    public void whenRequestSearchInCurrentTabThenTabViewLoadText() {
        browserPresenter.attachTabView(mockTabView);
        restoreSession();
        browserPresenter.requestSearchInCurrentTab(anyString());
        verify(mockTabView, times(1)).loadUrl(anyString());
    }

    @Test
    public void whenRequestSearchInNewTabWithBrowserAttachedThenCreateAndShowNewTab() {
        browserPresenter.attachTabView(mockTabView);
        restoreSession();
        browserPresenter.requestSearchInNewTab(anyString());
        verify(mockBrowserView, times(1)).createNewTab(anyString());
        verify(mockBrowserView, times(2)).showTab(anyString());
    }

    @Test
    public void whenRequestSearchInNewTabWithBrowserAttachedThenTabViewLoadText() {
        browserPresenter.attachTabView(mockTabView);
        restoreSession();
        browserPresenter.requestSearchInNewTab(anyString());
        verify(mockTabView, times(1)).loadUrl(anyString());
    }

    @Test
    public void whenRequestSearchInNewTabWithBrowserNotAttachedThenCreateAndOpenNewTabAfterBrowserIsAttached() {
        final String text = "test";
        browserPresenter.detachBrowserView();
        browserPresenter.requestSearchInNewTab(text);
        browserPresenter.attachBrowserView(mockBrowserView);
        browserPresenter.attachTabView(mockTabView);
        restoreSession();
        verify(mockBrowserView, times(1)).createNewTab(anyString());
        verify(mockBrowserView, times(1)).showTab(anyString());
    }

    @Test
    public void whenRequestSearchInNewTabWithBrowserNotAttachedThenTabViewLoadTextAfterBrowserIsAttached() {
        final String text = "test";
        browserPresenter.detachBrowserView();
        browserPresenter.requestSearchInNewTab(text);
        browserPresenter.attachBrowserView(mockBrowserView);
        browserPresenter.attachTabView(mockTabView);
        restoreSession();
        verify(mockTabView, times(1)).loadUrl(anyString());
    }

    @Test
    public void whenRequestAssistWithBrowserAttachedThenCreateAndOpenNewTab() {
        browserPresenter.attachTabView(mockTabView);
        restoreSession();
        browserPresenter.requestAssist();
        verify(mockBrowserView, times(1)).createNewTab(anyString());
        verify(mockBrowserView, times(2)).showTab(anyString());
    }

    @Test
    public void whenRequestAssistWithBrowserAttachedThenClearOmnibar() {
        browserPresenter.attachTabView(mockTabView);
        restoreSession();
        verify(mockOmnibarView, times(1)).clearText();
        browserPresenter.requestAssist();
        verify(mockOmnibarView, times(2)).clearText();
    }

    @Test
    public void whenRequestAssistWithBrowserNotAttachedThenCreateAndOpenNewTabAfterBrowserIsAttached() {
        browserPresenter.detachBrowserView();
        browserPresenter.requestAssist();
        browserPresenter.attachBrowserView(mockBrowserView);
        browserPresenter.attachTabView(mockTabView);
        restoreSession();

        verify(mockBrowserView, times(1)).createNewTab(anyString());
        verify(mockBrowserView, times(1)).showTab(anyString());
    }

    @Test
    public void whenRequestAssistWithBrowserNotAttachedhenClearOmnibarAfterBrowserIsAttached() {
        browserPresenter.detachBrowserView();
        browserPresenter.requestAssist();
        browserPresenter.attachBrowserView(mockBrowserView);
        browserPresenter.attachTabView(mockTabView);
        restoreSession();
        verify(mockOmnibarView, times(1)).clearText();
        verify(mockOmnibarView, times(1)).clearText();
    }

    @Test
    public void whenOmnibarFocusChangeToFalseThenHaveNoInteractionWithOmnibarView() {
        final boolean focus = false;
        browserPresenter.omnibarFocusChanged(focus);
        verifyZeroInteractions(mockOmnibarView);
    }

    @Test
    public void whenOmnibarFocusChangeToTrueThenSetOmnibarEdiable() {
        final boolean focus = true;
        browserPresenter.omnibarFocusChanged(focus);
        verify(mockOmnibarView, times(1)).setEditing(true);
    }

    @Test
    public void whenCancelOmnibarFocusThenSetOmnibarNotEditable() {
        browserPresenter.cancelOmnibarFocus();
        verify(mockOmnibarView, times(1)).setEditing(false);
    }

    @Test
    public void whenOmnibarTextChangedAndNotEditingThenNoInteractionWithOmnibarView() {
        final String text = "test";
        browserPresenter.omnibarTextChanged(text);
        verifyZeroInteractions(mockOmnibarView);
    }

    @Test
    public void whenOmnibarTextChangedToNonEmptyStringAndOmnibarEditingIsTrueThenShowDeleteButton() {
        final String text = "test";
        browserPresenter.omnibarFocusChanged(true);
        browserPresenter.omnibarTextChanged(text);
        verify(mockOmnibarView, times(1)).setDeleteAllTextButtonVisible(true);
    }

    @Test
    public void whenOmnibarTextChangedToEmptyStringAndOmnibarEditingIsTrueThenHideDeleteButton() {
        final String text = "";
        browserPresenter.omnibarFocusChanged(true);
        browserPresenter.omnibarTextChanged(text);
        verify(mockOmnibarView, times(1)).setDeleteAllTextButtonVisible(false);
    }

    @Test
    public void whenCancelOmnibarFocusFromLoadedUrlThenShowPreviousUrl() {
        final String currentUrl = mockTabs.get(1).getCurrentUrl();
        restoreSession();
        browserPresenter.openTab(1);
        browserPresenter.cancelOmnibarFocus();
        verify(mockOmnibarView, atLeast(1)).displayText(currentUrl);
    }

    @Test
    public void whenCancelOmnibarTextThenClearOmnibarText() {
        browserPresenter.cancelOmnibarText();
        verify(mockOmnibarView, times(1)).clearText();
    }

    @Test
    public void whenNavigateHistoryForwardThenTabViewGoForward() {
        browserPresenter.attachTabView(mockTabView);
        browserPresenter.navigateHistoryForward();
        verify(mockTabView, times(1)).goForward();
    }

    @Test
    public void whenNavigateHistoryBackwardThenTabViewGoBack() {
        browserPresenter.attachTabView(mockTabView);
        browserPresenter.navigateHistoryBackward();
        verify(mockTabView, times(1)).goBack();
    }

    @Test
    public void whenRefreshCurrentPageThenTabViewReload() {
        browserPresenter.attachTabView(mockTabView);
        browserPresenter.refreshCurrentPage();
        verify(mockTabView, times(1)).reload();
    }

    @Test
    public void whenOpenTabSwitcherAndPressBackThenDismissTabSwitcher() {
        restoreSession();
        browserPresenter.openTabSwitcher();
        browserPresenter.attachTabSwitcherView(mockTabSwitcherView);
        browserPresenter.handleBackNavigation();
        verify(mockMainView, times(1)).dismissTabSwitcher();
    }

    @Test
    public void whenOpenTabSwitcherAndPressBackThenHandleBackNavigationReturnsTrue() {
        restoreSession();
        browserPresenter.openTabSwitcher();
        browserPresenter.attachTabSwitcherView(mockTabSwitcherView);
        assertTrue(browserPresenter.handleBackNavigation());
    }

    @Test
    public void whenTabSwitcherIsClosedAndPressBackThenHaveNoInteractionWithTabSwitcherView() {
        when(mockTabView.canGoBack()).thenReturn(true);
        browserPresenter.attachTabView(mockTabView);
        browserPresenter.handleBackNavigation();
        verifyZeroInteractions(mockTabSwitcherView);
    }

    @Test
    public void whenOmnibarIsEditableAndPressBackThenSetOmnibarEditableFalse() {
        browserPresenter.omnibarFocusChanged(true);
        browserPresenter.handleBackNavigation();
        verify(mockOmnibarView, atLeast(1)).setEditing(false);
    }

    @Test
    public void whenTabSwitcherIsClosedAndTabViewCanGoBackThenNavigateHistoryBackward() {
        when(mockTabView.canGoBack()).thenReturn(true);
        browserPresenter.attachTabView(mockTabView);
        browserPresenter.handleBackNavigation();
        verify(mockTabView, times(1)).goBack();
    }

    @Test
    public void whenTabSwitcherIsClosedAndTabViewCanGoBackThenHandleBackNavigationReturnsTrue() {
        when(mockTabView.canGoBack()).thenReturn(true);
        browserPresenter.attachTabView(mockTabView);
        assertTrue(browserPresenter.handleBackNavigation());
    }

    @Test
    public void whenTabSwitcherIsClosedAndTabViewCannotGoBackThenHandleBackNavigationReturnsFalse() {
        when(mockTabView.canGoBack()).thenReturn(false);
        browserPresenter.attachTabView(mockTabView);
        assertFalse(browserPresenter.handleBackNavigation());
    }

    @Test
    public void whenViewBookmarksThenMainViewNavigateToBookamrks() {
        browserPresenter.viewBookmarks();
        verify(mockMainView, times(1)).navigateToBookmarks();
    }

    @Test
    public void whenRequestSaveCurrentPageAsBookmarkThenMainViewShowConfirmSaveBookmark() {
        startSession();
        browserPresenter.openNewTab();
        browserPresenter.requestSaveCurrentPageAsBookmark();
        verify(mockMainView, times(1)).showConfirmSaveBookmark(any(BookmarkEntity.class));
    }

    @Test
    public void whenSaveBookmarkThenInsertBookmarkInRepository() {
        BookmarkEntity bookmark = createMockBookmark();
        browserPresenter.saveBookmark(bookmark);
        verify(mockBookmarkRepository, times(1)).insert(bookmark);
    }

    @Test
    public void whenLoadBookmarkThenTabViewLoadBookmarkUrl() {
        BookmarkEntity bookmark = createMockBookmark();
        browserPresenter.attachTabView(mockTabView);
        startSession();
        browserPresenter.loadBookmark(bookmark);
        verify(mockTabView, times(1)).loadUrl(bookmark.getUrl());
    }
}
