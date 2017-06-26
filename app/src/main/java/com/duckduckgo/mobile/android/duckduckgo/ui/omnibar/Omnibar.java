package com.duckduckgo.mobile.android.duckduckgo.ui.omnibar;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.design.widget.AppBarLayout;
import android.support.transition.Fade;
import android.support.transition.Transition;
import android.support.transition.TransitionManager;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.duckduckgo.mobile.android.duckduckgo.R;
import com.duckduckgo.mobile.android.duckduckgo.util.KeyboardUtils;

import butterknife.BindView;
import butterknife.ButterKnife;

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

public class Omnibar extends AppBarLayout implements OmnibarView {

    public interface OnSearchListener {
        void onTextSearched(@NonNull String text);

        void onTextChanged(@NonNull String newText);

        void onCancel();
    }

    public interface OnFocusListener {
        void onFocusChanged(boolean focus);
    }

    @BindView(R.id.omnibar_toolbar)
    Toolbar toolbar;

    @BindView(R.id.omnibar_edit_text)
    EditText searchEditText;

    @BindView(R.id.omnibar_back_image_button)
    ImageButton backImageButton;

    @BindView(R.id.omnibar_progress_bar)
    ProgressBar progressBar;

    private OnSearchListener onSearchListener;
    private OnFocusListener onFocusListener;

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
        backImageButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onSearchListener.onCancel();
            }
        });
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
    public void setRefreshEnabled(boolean enabled) {
        MenuItem reloadMenuItem = toolbar.getMenu().findItem(R.id.action_refresh);
        reloadMenuItem.setEnabled(enabled);
    }

    @Override
    public void setEditing(boolean editing) {
        int visibility = editing ? View.VISIBLE : View.GONE;
        if (backImageButton.getVisibility() == visibility) return;
        TransitionManager.beginDelayedTransition(this);
        backImageButton.setVisibility(visibility);

        Menu menu = toolbar.getMenu();
        menu.setGroupVisible(R.id.group_menu, !editing);
    }

    @Override
    public void setDeleteAllTextButtonVisible(boolean visible) {
        MenuItem menuItem = toolbar.getMenu().findItem(R.id.action_delete_all_text);
        if (menuItem.isVisible() == visible) return;
        menuItem.setVisible(visible);
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

    public void setOnFocusListener(OnFocusListener onFocusListener) {
        this.onFocusListener = onFocusListener;
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
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (onSearchListener != null) {
                    String text = s.toString();
                    onSearchListener.onTextChanged(text);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        searchEditText.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (onFocusListener != null) {
                    onFocusListener.onFocusChanged(hasFocus);
                }
            }
        });
    }

    private boolean wasEnterPressed(KeyEvent event) {
        return event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
    }
}
