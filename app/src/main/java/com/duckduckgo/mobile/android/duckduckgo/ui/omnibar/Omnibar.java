package com.duckduckgo.mobile.android.duckduckgo.ui.omnibar;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.design.widget.AppBarLayout;
import android.support.transition.Fade;
import android.support.transition.Transition;
import android.support.transition.TransitionManager;
import android.support.v7.widget.Toolbar;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.duckduckgo.mobile.android.duckduckgo.R;
import com.duckduckgo.mobile.android.duckduckgo.util.KeyboardUtils;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Created by fgei on 6/14/17.
 */

public class Omnibar extends AppBarLayout implements OmnibarView {

    public interface OnSearchListener {
        void onTextSearched(@NonNull String text);
    }

    @BindView(R.id.omnibar_toolbar)
    Toolbar toolbar;

    @BindView(R.id.omnibar_edit_text)
    EditText searchEditText;

    @BindView(R.id.omnibar_progress_bar)
    ProgressBar progressBar;

    private OnSearchListener onSearchListener;

    public Omnibar(Context context) {
        super(context);
        init();
    }

    public Omnibar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.view_omnibar, this);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        ButterKnife.bind(this);
        initSearchEditText(searchEditText);
    }

    @Override
    public void displayText(@NonNull String text) {
        searchEditText.setText(text);
    }

    @Override
    public void clearText() {
        searchEditText.setText("");
    }

    @Override
    public void clearFocus() {
        searchEditText.clearFocus();
    }

    @Override
    public void requestSearchFocus() {
        searchEditText.requestFocus();
    }

    @Override
    public void setBackEnabled(boolean enabled) {
        MenuItem backMenuItem = toolbar.getMenu().findItem(R.id.action_go_back);
        backMenuItem.setEnabled(enabled);
    }

    @Override
    public void setForwardEnabled(boolean enabled) {
        MenuItem forwardMenuItem = toolbar.getMenu().findItem(R.id.action_go_forward);
        forwardMenuItem.setEnabled(enabled);
    }

    @Override
    public void setRefreshEnabled(boolean enabled) {
        MenuItem reloadMenuItem = toolbar.getMenu().findItem(R.id.action_refresh);
        reloadMenuItem.setEnabled(enabled);
    }

    @Override
    public void showProgressBar() {
        if (progressBar.getVisibility() == View.VISIBLE) return;
        TransitionManager.beginDelayedTransition(this);
        progressBar.setVisibility(View.VISIBLE);
    }

    @Override
    public void hideProgressBar() {
        if (progressBar.getVisibility() == View.GONE) return;
        Transition fade = new Fade();
        fade.setStartDelay(400);
        TransitionManager.beginDelayedTransition(this, fade);
        progressBar.setVisibility(View.GONE);
    }

    @Override
    public void onProgressChanged(int newProgress) {
        progressBar.setProgress(newProgress);
    }

    public void inflateMenu(int resId) {
        toolbar.inflateMenu(resId);
    }

    public Menu getMenu() {
        return toolbar.getMenu();
    }

    public void setOnMenuItemClickListener(Toolbar.OnMenuItemClickListener listener) {
        toolbar.setOnMenuItemClickListener(listener);
    }

    public void setOnSearchListener(OnSearchListener onSearchListener) {
        this.onSearchListener = onSearchListener;
    }

    private void initSearchEditText(EditText searchEditText) {
        searchEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH || wasEnterPressed(event)) {
                    String text = v.getText().toString().trim();
                    if (onSearchListener != null) {
                        onSearchListener.onTextSearched(text);
                    }
                    KeyboardUtils.hideKeyboard(v);
                    v.clearFocus();
                    return true;
                }
                return false;
            }
        });
    }

    private boolean wasEnterPressed(KeyEvent event) {
        return event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
    }
}
