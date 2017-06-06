package com.duckduckgo.mobile.android.duckduckgo.ui.browser;

import com.duckduckgo.mobile.android.duckduckgo.ui.browser.tab.Tab;
import com.duckduckgo.mobile.android.duckduckgo.ui.browser.tab.TabManager;
import com.duckduckgo.mobile.android.duckduckgo.ui.browser.tab.TabView;
import com.duckduckgo.mobile.android.duckduckgo.util.AppUrls;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * Created by fgei on 5/22/17.
 */

public class BrowserPresenterTest {

    private BrowserView mockBrowserView;
    private OmnibarView mockOmnibarView;
    private TabView mockTabView;
    private TabManager mockTabManager;
    private BrowserPresenter browserPresenter;
    private Tab mockCurrentTab;

    @Before
    public void setup() {
        mockBrowserView = mock(BrowserView.class);
        mockOmnibarView = mock(OmnibarView.class);
        mockTabView = mock(TabView.class);
        mockTabManager = mock(TabManager.class);
        mockCurrentTab = Tab.createNewTab();

        browserPresenter = new BrowserPresenterImpl(mockTabManager);
        browserPresenter.attachBrowserView(mockBrowserView);
        browserPresenter.attachOmnibarView(mockOmnibarView);
        browserPresenter.attachTabView(mockTabView);
    }

    @Test
    public void whenDetachViewsThenHaveNoInteractionWithViews() {
        browserPresenter.detachViews();
        verifyZeroInteractions(mockBrowserView);
        verifyZeroInteractions(mockOmnibarView);
        verifyZeroInteractions(mockTabView);
    }

    @Test
    public void whenDetachTabViewThenHaveNoInteractionWithTabView() {
        browserPresenter.detachTabView();
        verifyZeroInteractions(mockTabView);
    }

    @Test
    public void whenLoadAndCurrentTabIsNullCreateNewTab() {
        when(mockTabManager.getCurrentTab()).thenReturn(null);
        browserPresenter.load();
        verify(mockTabManager, times(1)).createNewTab();
    }

    @Test
    public void whenLoadThenShowCurrentTab() {
        mockCurrentTab.id = "random-id";
        when(mockTabManager.getCurrentTab()).thenReturn(mockCurrentTab);
        browserPresenter.load();
        verify(mockBrowserView, times(1)).switchToTab(mockCurrentTab.id);
    }

    @Test
    public void whenRestorePresenterThenRestoreTabManagerTabs() {
        final int currentIndex = 0;
        browserPresenter.restore(ArgumentMatchers.<Tab>anyList(), currentIndex);
        verify(mockTabManager, times(1)).setTabs(ArgumentMatchers.<Tab>anyList());
    }

    @Test
    public void whenRestorePresenterThenRestoreTabManagerCurrentTab() {
        final List<Tab> list = new ArrayList<>();
        browserPresenter.restore(list, anyInt());
        verify(mockTabManager, times(1)).selectTab(anyInt());
    }

    @Test
    public void whenOpenTabSwitcherThenNavigateToTabSwitcher() {
        browserPresenter.openTabSwitcher();
        verify(mockBrowserView, times(1)).navigateToTabSwitcher(ArgumentMatchers.<Tab>anyList());
    }

    @Test
    public void whenCreateNewTabThenCreateNewTabInTabManager() {
        browserPresenter.createNewTab();
        verify(mockTabManager, times(1)).createNewTab();
    }

    @Test
    public void whenOpenTabThenSelectCorrectTabInTabManager() {
        browserPresenter.openTab(anyInt());
        verify(mockTabManager, times(1)).selectTab(anyInt());
    }

    @Test
    public void whenRemoveTabsThenRemoveTabsInTabManager() {
        browserPresenter.removeTabs(ArgumentMatchers.<Integer>anyList());
        verify(mockTabManager, times(1)).removeTabs(ArgumentMatchers.<Integer>anyList());
    }

    @Test
    public void whenRemoveAllTabsThenClearTabManager() {
        browserPresenter.removeAllTabs();
        verify(mockTabManager, times(1)).removeAll();
    }

    @Test
    public void whenRequestSearchWithUrlThenLoadUrlInTabView() {
        final String urlInput = "https://test.com";
        browserPresenter.requestSearch(urlInput);
        verify(mockTabView, times(1)).loadUrl(urlInput);
        verifyZeroInteractions(mockOmnibarView);
    }

    @Test
    public void whenRequestSearchWithQueryThenLoadUrlInTabView() {
        final String query = "ciao";
        final String url = AppUrls.getSearchUrl(query);
        browserPresenter.requestSearch(query);
        verify(mockTabView, times(1)).loadUrl(url);
    }

    @Test
    public void whenRequestSearchWithNullThenHaveNoInteractionWithViews() {
        final String url = null;
        browserPresenter.requestSearch(url);
        verifyZeroInteractions(mockOmnibarView);
        verifyZeroInteractions(mockBrowserView);
        verifyZeroInteractions(mockTabView);
    }

    @Test
    public void whenRequestSearchInNewTabThenCreateNewTab() {
        final String query = "ciao";
        browserPresenter.requestSearchInNewTab(query);
        verify(mockTabManager, times(1)).createNewTab();
    }

    @Test
    public void whenRequestAssistThenCreateNewTab() {
        browserPresenter.requestAssist();
        verify(mockTabManager, times(1)).createNewTab();
    }

    @Test
    public void whenRequestSearchWithUrlThenHaveNoInteractionWithOmnibarView() {
        browserPresenter.requestSearch(anyString());
        verifyZeroInteractions(mockOmnibarView);
    }

    @Test
    public void whenReqeustSearchWithQueryThenHaveNoInteractionWithOmnibarView() {
        browserPresenter.requestSearch(anyString());
        verifyZeroInteractions(mockOmnibarView);
    }

    @Test
    public void whenRequestAssistThenHaveNoInteractionWithTabView() {
        browserPresenter.requestAssist();
        verifyZeroInteractions(mockTabView);
    }

    @Test
    public void whenNavigateHistoryBackThenGoBackAndNotGoForwardInTabView() {
        browserPresenter.navigateHistoryBackward();
        verify(mockTabView, times(1)).goBack();
        verify(mockTabView, times(0)).goForward();
        verifyZeroInteractions(mockOmnibarView);
    }

    @Test
    public void whenNavigateHistoryForwardThenGoForwardAndNotGotBackInTabView() {
        browserPresenter.navigateHistoryForward();
        verify(mockTabView, times(1)).goForward();
        verify(mockTabView, times(0)).goBack();
        verifyZeroInteractions(mockOmnibarView);
    }

    @Test
    public void whenRefreshCurrentPageThenReloadInOmnibarView() {
        browserPresenter.refreshCurrentPage();
        verify(mockTabView, times(1)).reload();
        verify(mockTabView, times(0)).goBack();
        verify(mockTabView, times(0)).goForward();
        verifyZeroInteractions(mockOmnibarView);
    }

    @Test
    public void whenOnPageStartedThenDisplayTextInOmnibarView() {
        final String url = "https://test.com/";
        when(mockTabManager.getCurrentTab()).thenReturn(mockCurrentTab);
        browserPresenter.onPageStarted(url);
        verify(mockOmnibarView, times(1)).displayText(url);
    }

    @Test
    public void whenOnPageStartedWithNullThenDisplayTextWithEmptyStringInOmnibarView() {
        final String url = null;
        when(mockTabManager.getCurrentTab()).thenReturn(mockCurrentTab);
        browserPresenter.onPageStarted(url);
        verify(mockOmnibarView, times(1)).displayText("");
    }

    @Test
    public void whenOnPageStartedWithDuckDuckGoUrlThenDisplayQueryInOmnibarView() {
        final String url = "https://www.duckduckgo.com/?ko=-1&kl=wt-wt&q=test";
        final String query = "test";
        when(mockTabManager.getCurrentTab()).thenReturn(mockCurrentTab);
        browserPresenter.onPageStarted(url);
        verify(mockOmnibarView, times(1)).displayText(query);
    }

    @Test
    public void whenOnPageFinishedThenUpdateOmnibarNavigationMenu() {
        String text = "https://test.com/";
        when(mockTabManager.getCurrentTab()).thenReturn(mockCurrentTab);
        final boolean canGoBack = true;
        when(mockTabView.canGoBack()).thenReturn(canGoBack);
        final boolean canGoForward = false;
        when(mockTabView.canGoForward()).thenReturn(canGoForward);
        browserPresenter.onPageFinished(text);
        verify(mockTabView, times(1)).canGoBack();
        verify(mockTabView, times(1)).canGoForward();
        verify(mockOmnibarView, times(1)).setForwardEnabled(canGoForward);
        verify(mockOmnibarView, times(1)).setBackEnabled(canGoBack);
        verifyNoMoreInteractions(mockBrowserView);
    }

    @Test
    public void whenCanGoBackThenHandleBackHistorySucceeds() {
        when(mockTabView.canGoBack()).thenReturn(true);
        assertTrue(browserPresenter.handleBackHistory());
        verify(mockTabView, times(1)).goBack();
    }

    @Test
    public void whenCannotGoBackThenHandleBackHistoryFails() {
        when(mockTabView.canGoBack()).thenReturn(false);
        assertFalse(browserPresenter.handleBackHistory());
        verify(mockTabView, never()).goBack();
    }

    @Test
    public void whenOnProgressChangedWithProgressNotCompleteThenShowProgressBarAndShowProgress() {
        final int newProgress = 0;
        browserPresenter.onProgressChanged(newProgress);
        verifyZeroInteractions(mockBrowserView);
        verify(mockOmnibarView, times(1)).showProgressBar();
        verify(mockOmnibarView, times(1)).onProgressChanged(newProgress);
    }

    @Test
    public void whenOnProgressChangedWithProgressCompleteThenShowProgressAndHideProgressBar() {
        final int newProgress = 100;
        browserPresenter.onProgressChanged(newProgress);
        verifyZeroInteractions(mockBrowserView);
        verify(mockOmnibarView, times(1)).onProgressChanged(newProgress);
        verify(mockOmnibarView, times(1)).hideProgressBar();
        verifyNoMoreInteractions(mockOmnibarView);
    }
}
