package com.duckduckgo.mobile.android.duckduckgo.ui.browser;

import com.duckduckgo.mobile.android.duckduckgo.util.AppUrls;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
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
    private BrowserPresenter browserPresenter;

    @Before
    public void setup() {
        mockBrowserView = mock(BrowserView.class);
        mockOmnibarView = mock(OmnibarView.class);

        browserPresenter = new BrowserPresenterImpl();
        browserPresenter.attachBrowserView(mockBrowserView);
        browserPresenter.attachOmnibarView(mockOmnibarView);
    }

    @Test
    public void whenDetachViewsThenHaveNoInteractionWithViews() {
        browserPresenter.detachViews();
        verifyZeroInteractions(mockBrowserView);
        verifyZeroInteractions(mockOmnibarView);
    }

    @Test
    public void whenRequestSearchWithUrlThenLoadUrlInBrowserView() {
        final String urlInput = "https://test.com";
        browserPresenter.requestSearch(urlInput);
        verify(mockBrowserView, times(1)).loadUrl(urlInput);
        verifyZeroInteractions(mockOmnibarView);
    }

    @Test
    public void whenRequestSearchWithQueryThenLoadUrlInBrowserView() {
        final String query = "ciao";
        final String url = AppUrls.getSearchUrl(query);
        browserPresenter.requestSearch(query);
        verify(mockBrowserView, times(1)).loadUrl(url);
    }

    @Test
    public void whenRequestSearchWithNullThenHaveNoInteractionWithViews() {
        final String url = null;
        browserPresenter.requestSearch(url);
        verifyZeroInteractions(mockOmnibarView);
        verifyZeroInteractions(mockBrowserView);
    }

    @Test
    public void whenRequestAssistThenClearTextAndRequestFocusInOmnibarView() {
        browserPresenter.requestAssist();
        verify(mockOmnibarView, times(1)).clearText();
        verify(mockOmnibarView, times(1)).requestFocus();
        verifyZeroInteractions(mockBrowserView);
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
    public void whenRequestAssistThenHaveNoInteractionWithBrowserView() {
        browserPresenter.requestAssist();
        verifyZeroInteractions(mockBrowserView);
    }

    @Test
    public void whenNavigateHistoryBackThenGoBackAndNotGoForwardInBrowserView() {
        browserPresenter.navigateHistoryBackward();
        verify(mockBrowserView, times(1)).goBack();
        verify(mockBrowserView, times(0)).goForward();
        verifyZeroInteractions(mockOmnibarView);
    }

    @Test
    public void whenNavigateHistoryForwardThenGoForwardAndNotGotBackInBrowserView() {
        browserPresenter.navigateHistoryForward();
        verify(mockBrowserView, times(1)).goForward();
        verify(mockBrowserView, times(0)).goBack();
        verifyZeroInteractions(mockOmnibarView);
    }

    @Test
    public void whenRefreshCurrentPageThenReloadInOmnibarView() {
        browserPresenter.refreshCurrentPage();
        verify(mockBrowserView, times(1)).reload();
        verify(mockBrowserView, times(0)).goBack();
        verify(mockBrowserView, times(0)).goForward();
        verifyZeroInteractions(mockOmnibarView);
    }

    @Test
    public void whenOnPageStartedThenDisplayTextInOmnibarView() {
        browserPresenter.onPageStarted(anyString());
        verify(mockOmnibarView, times(1)).displayText(anyString());
    }

    @Test
    public void whenOnPageStartedWithNullThendisplayTextWithEmptyStringInOmnibarView() {
        final String url = null;
        browserPresenter.onPageStarted(url);
        verify(mockOmnibarView, times(1)).displayText("");
    }

    @Test
    public void whenOnPageFinishedThenUpdateOmnibarNavigationMenu() {
        String text = "https://test.com/";
        final boolean canGoBack = true;
        when(mockBrowserView.canGoBack()).thenReturn(canGoBack);
        final boolean canGoForward = false;
        when(mockBrowserView.canGoForward()).thenReturn(canGoForward);
        browserPresenter.onPageFinished(text);
        verify(mockBrowserView, times(1)).canGoBack();
        verify(mockBrowserView, times(1)).canGoForward();
        verify(mockOmnibarView, times(1)).setForwardEnabled(canGoForward);
        verify(mockOmnibarView, times(1)).setBackEnabled(canGoBack);
        verifyNoMoreInteractions(mockBrowserView);
    }

    @Test
    public void whenCanGoBackThenHandleBackHistorySucceeds() {
        when(mockBrowserView.canGoBack()).thenReturn(true);
        assertTrue(browserPresenter.handleBackHistory());
        verify(mockBrowserView, times(1)).goBack();
    }

    @Test
    public void whenCannotGoBackThenHandleBackHistoryFails() {
        when(mockBrowserView.canGoBack()).thenReturn(false);
        assertFalse(browserPresenter.handleBackHistory());
        verify(mockBrowserView, never()).goBack();
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
