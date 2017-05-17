package com.duckduckgo.mobile.android.duckduckgo.ui;

import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.TextView;

import com.duckduckgo.mobile.android.duckduckgo.R;
import com.duckduckgo.mobile.android.duckduckgo.ui.browser.BrowserContract;
import com.duckduckgo.mobile.android.duckduckgo.ui.browser.BrowserPresenter;
import com.duckduckgo.mobile.android.duckduckgo.ui.browser.DDGWebViewClient;
import com.duckduckgo.mobile.android.duckduckgo.util.KeyboardHelper;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity implements BrowserContract.View {

    @BindView(R.id.browser_web_view) WebView webView;
    @BindView(R.id.appbar_toolbar) Toolbar toolbar;
    @BindView(R.id.appbar_edit_text) EditText searchEditText;

    BrowserContract.Presenter browserPresenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.e("handle_intent", "onCreate");
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        browserPresenter = new BrowserPresenter(this);
        initUI();

        handleIntent(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e("handle_intent", "onResume");
        browserPresenter.start();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.e("handle_intent", "onNewIntent");
        handleIntent(intent);
    }

    @Override
    public void onBackPressed() {
        if(browserPresenter.handleBackHistory()) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void setPresenter(BrowserContract.Presenter presenter) {
        this.browserPresenter = presenter;
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
    public void setCanGoBackEnabled(boolean enabled) {
        MenuItem backMenuItem = toolbar.getMenu().findItem(R.id.action_go_back);
        backMenuItem.setEnabled(enabled);
    }

    @Override
    public void setCanGoForwardEnabled(boolean enabled) {
        MenuItem forwardMenuItem = toolbar.getMenu().findItem(R.id.action_go_forward);
        forwardMenuItem.setEnabled(enabled);
    }

    @Override
    public void reload() {
        webView.reload();
    }

    @Override
    public void clearSearchBar() {
        searchEditText.setText("");
    }

    @Override
    public void focusSearchBar() {
        searchEditText.requestFocus();
    }

    private void initUI() {
        initWebView(webView);
        initToolbar(toolbar);
        initSearchEditText(searchEditText);
    }

    private void initWebView(WebView webView) {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptCanOpenWindowsAutomatically(false);
        webSettings.setJavaScriptEnabled(true);
        webView.setWebViewClient(new DDGWebViewClient(browserPresenter));
    }

    private void initToolbar(Toolbar toolbar) {
        toolbar.inflateMenu(R.menu.browser);
        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch(item.getItemId()) {
                    case R.id.action_go_back:
                        browserPresenter.navigateHistoryBackward();
                        return true;
                    case R.id.action_go_forward:
                        browserPresenter.navigateHistoryForward();
                        return true;
                    case R.id.action_refresh:
                        browserPresenter.refreshCurrentPage();
                        return true;
                }
                return false;
            }
        });
    }

    private void initSearchEditText(EditText searchEditText) {
        searchEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if(actionId == EditorInfo.IME_ACTION_SEARCH) {
                    String query = v.getText().toString().trim();
                    browserPresenter.requestQuerySearch(query);
                    KeyboardHelper.hideKeyboard(v);
                    return true;
                }
                return false;
            }
        });
    }

    private void handleIntent(Intent intent) {
        String action = intent.getAction();
        Log.e("handle_intent", "intent, action: "+action);
        if(action.equals(Intent.ACTION_VIEW)) {
            String url = intent.getDataString();
            browserPresenter.requestLoadUrl(url);
        } else if(action.equals(Intent.ACTION_WEB_SEARCH)) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            browserPresenter.requestQuerySearch(query);
        } else if(action.equals(Intent.ACTION_ASSIST)) {
             browserPresenter.requestAssist();
        }
    }
}
