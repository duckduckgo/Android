/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.mobile.android.duckduckgo.ui.main;

import android.app.Activity;
import android.app.SearchManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.ColorRes;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.duckduckgo.mobile.android.duckduckgo.Injector;
import com.duckduckgo.mobile.android.duckduckgo.R;
import com.duckduckgo.mobile.android.duckduckgo.ui.bookmarks.BookmarkEntity;
import com.duckduckgo.mobile.android.duckduckgo.ui.bookmarks.BookmarksActivity;
import com.duckduckgo.mobile.android.duckduckgo.ui.browser.BrowserFragment;
import com.duckduckgo.mobile.android.duckduckgo.ui.browser.BrowserPresenter;
import com.duckduckgo.mobile.android.duckduckgo.ui.editbookmark.EditBookmarkDialogFragment;
import com.duckduckgo.mobile.android.duckduckgo.ui.navigator.Navigator;
import com.duckduckgo.mobile.android.duckduckgo.ui.tabswitcher.TabSwitcherFragment;

public class MainActivity extends AppCompatActivity implements MainView, EditBookmarkDialogFragment.OnEditBookmarkListener {

    private static final int REQUEST_PICK_BOOKMARK = 200;

    private static final int ACTIVITY_CONTAINER = android.R.id.content;

    private BrowserPresenter browserPresenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        browserPresenter = Injector.injectBrowserPresenter();

        BrowserFragment browserFragment = (BrowserFragment) getSupportFragmentManager().findFragmentByTag(BrowserFragment.TAG);
        if (browserFragment == null) browserFragment = BrowserFragment.newInstance();

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(ACTIVITY_CONTAINER, browserFragment, BrowserFragment.TAG)
                    .commit();
            getSupportFragmentManager().executePendingTransactions();
        }

        handleIntent(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        browserPresenter.attachMainview(this);
    }

    @Override
    protected void onPause() {
        browserPresenter.detachMainView();
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    public void onBackPressed() {
        if (browserPresenter.handleBackNavigation()) {
            return;
        }
        super.onBackPressed();
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
    public void showConfirmSaveBookmark(@NonNull BookmarkEntity bookmarkEntity) {
        EditBookmarkDialogFragment dialog = EditBookmarkDialogFragment.newInstance(R.string.bookmark_dialog_title_save, bookmarkEntity);
        dialog.show(getSupportFragmentManager(), EditBookmarkDialogFragment.TAG);
    }

    @Override
    public void navigateToBookmarks() {
        Navigator.navigateToBookmarks(this, REQUEST_PICK_BOOKMARK);
    }

    @Override
    public void navigateToTabSwitcher() {
        showTabSwitcher();
    }

    @Override
    public void dismissTabSwitcher() {
        removeTabSwitcher();
    }

    @Override
    public void copyUrlToClipboard(@NonNull String url) {
        copyTextToClipboard(getString(R.string.main_label_copy_url_to_clipboard), url);
        Toast.makeText(this, R.string.main_copy_to_clipboard_success, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBookmarkEdited(BookmarkEntity bookmark) {
        browserPresenter.saveBookmark(bookmark);
    }

    private void handleIntent(Intent intent) {
        String action = intent.getAction();
        switch (action) {
            case Intent.ACTION_VIEW:
                handleActionView(intent);
                break;
            case Intent.ACTION_WEB_SEARCH:
                handleActionWebSearch(intent);
                break;
            case Intent.ACTION_ASSIST:
                handleActionAssist();
                break;
        }
    }

    private void handleActionView(Intent intent) {
        String url = intent.getDataString();
        browserPresenter.requestSearchInNewTab(url);
    }

    private void handleActionWebSearch(Intent intent) {
        String query = intent.getStringExtra(SearchManager.QUERY);
        browserPresenter.requestSearchInNewTab(query);
    }

    private void handleActionAssist() {
        browserPresenter.requestAssist();
    }

    private void handleBookmarkResult(int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            BookmarkEntity bookmarkEntity = BookmarksActivity.getResultBookmark(data);
            if (bookmarkEntity != null) {
                browserPresenter.loadBookmark(bookmarkEntity);
            }
        }
    }

    private void showTabSwitcher() {
        updateStatusBarColor(R.color.tab_switcher_background, true);
        TabSwitcherFragment fragment = TabSwitcherFragment.newInstance();
        getSupportFragmentManager().beginTransaction().add(ACTIVITY_CONTAINER, fragment, TabSwitcherFragment.TAG).commit();
    }

    private void removeTabSwitcher() {
        updateStatusBarColor(R.color.colorPrimaryDark, false);
        TabSwitcherFragment fragment = (TabSwitcherFragment) getSupportFragmentManager().findFragmentByTag(TabSwitcherFragment.TAG);
        if (fragment == null) return;
        getSupportFragmentManager().beginTransaction().remove(fragment).commit();
    }

    private void updateStatusBarColor(@ColorRes int color, boolean lightText) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            getWindow().setStatusBarColor(ContextCompat.getColor(this, color));

            getWindow().getDecorView().setSystemUiVisibility(lightText ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    private void copyTextToClipboard(@NonNull String label, @NonNull String text) {
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        clipboardManager.setPrimaryClip(clip);
    }
}
