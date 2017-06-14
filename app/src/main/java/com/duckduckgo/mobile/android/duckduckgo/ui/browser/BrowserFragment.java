package com.duckduckgo.mobile.android.duckduckgo.ui.browser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.duckduckgo.mobile.android.duckduckgo.Injector;
import com.duckduckgo.mobile.android.duckduckgo.R;
import com.duckduckgo.mobile.android.duckduckgo.ui.bookmarks.BookmarkEntity;
import com.duckduckgo.mobile.android.duckduckgo.ui.bookmarks.BookmarksActivity;
import com.duckduckgo.mobile.android.duckduckgo.ui.browser.web.DDGWebChromeClient;
import com.duckduckgo.mobile.android.duckduckgo.ui.browser.web.DDGWebViewClient;
import com.duckduckgo.mobile.android.duckduckgo.ui.editbookmark.EditBookmarkDialogFragment;
import com.duckduckgo.mobile.android.duckduckgo.ui.navigator.Navigator;
import com.duckduckgo.mobile.android.duckduckgo.ui.omnibar.Omnibar;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;

/**
 * Created by fgei on 5/15/17.
 */

public class BrowserFragment extends Fragment implements BrowserView {

    public static final String TAG = BrowserFragment.class.getSimpleName();

    public static BrowserFragment newInstance() {
        return new BrowserFragment();
    }

    private static final int REQUEST_PICK_BOOKMARK = 200;

    @BindView(R.id.browser_web_view)
    WebView webView;

    @BindView(R.id.omnibar)
    Omnibar omnibar;

    private Unbinder unbinder;
    private BrowserPresenter browserPresenter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        browserPresenter = Injector.injectBrowserPresenter();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_browser, container, false);
        unbinder = ButterKnife.bind(this, rootView);
        browserPresenter.attachBrowserView(this);
        browserPresenter.attachOmnibarView(omnibar);
        return rootView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        initUI();

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        webView.resumeTimers();
        webView.onResume();
    }

    @Override
    public void onPause() {
        webView.pauseTimers();
        webView.onPause();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        browserPresenter.detachViews();
        unbinder.unbind();
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroyView();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_PICK_BOOKMARK:
                handleBookmarkResult(resultCode, data);
                break;
        }
    }

    @Override
    public void loadUrl(@NonNull String url) {
        webView.loadUrl(url);
    }

    @Override
    public void goBack() {
        webView.goBack();
    }

    @Override
    public void goForward() {
        webView.goForward();
    }

    @Override
    public boolean canGoBack() {
        return webView.canGoBack();
    }

    @Override
    public boolean canGoForward() {
        return webView.canGoForward();
    }

    @Override
    public void reload() {
        webView.reload();
    }

    @Override
    public void showConfirmSaveBookmark(@NonNull BookmarkEntity bookmarkEntity) {
        EditBookmarkDialogFragment dialog = EditBookmarkDialogFragment.newInstance(R.string.bookmark_dialog_title_save, bookmarkEntity);
        dialog.show(getFragmentManager(), EditBookmarkDialogFragment.TAG);
    }

    @Override
    public void navigateToBookmarks() {
        Navigator.navigateToBookmarks(this, REQUEST_PICK_BOOKMARK);
    }

    private void initUI() {
        initWebView(webView);
        initOmnibar(omnibar);
    }


    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView(WebView webView) {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptCanOpenWindowsAutomatically(false);
        webSettings.setJavaScriptEnabled(true);
        webView.setWebViewClient(new DDGWebViewClient(browserPresenter));
        webView.setWebChromeClient(new DDGWebChromeClient(browserPresenter));
    }

    private void initOmnibar(Omnibar omnibar) {
        omnibar.inflateMenu(R.menu.browser);
        omnibar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.action_go_back:
                        browserPresenter.navigateHistoryBackward();
                        return true;
                    case R.id.action_go_forward:
                        browserPresenter.navigateHistoryForward();
                        return true;
                    case R.id.action_refresh:
                        browserPresenter.refreshCurrentPage();
                        return true;
                    case R.id.action_bookmarks:
                        browserPresenter.viewBookmarks();
                        return true;
                    case R.id.action_save_bookmark:
                        browserPresenter.requestSaveCurrentPageAsBookmark();
                        return true;
                }
                return false;
            }
        });
        omnibar.setOnSearchListener(new Omnibar.OnSearchListener() {
            @Override
            public void onTextSearched(@NonNull String text) {
                browserPresenter.requestSearch(text);
            }
        });
    }

    private void handleBookmarkResult(int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            BookmarkEntity bookmarkEntity = BookmarksActivity.getResultBookmark(data);
            if (bookmarkEntity != null) {
                browserPresenter.loadBookmark(bookmarkEntity);
            }
        }
    }
}
