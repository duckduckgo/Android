package com.duckduckgo.mobile.android.activity;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

public class KeyboardService {
    private final Activity activity;

    public KeyboardService(Activity activity) {
        this.activity = activity;
    }

    public void hideKeyboardDelayed(final View view){
        view.postDelayed(new Runnable() {
            @Override
            public void run() {
                hideKeyboard(view);
            }
        }, 100);
    }

    public void showKeyboardDelayed(final View view) {
        view.postDelayed(new Runnable() {
            @Override
            public void run() {
                showKeyboard(view);
            }
        }, 100);
    }

    public void hideKeyboard(final View view) {
        activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    public void showKeyboard(final View view) {
        view.requestFocus();
        activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

        InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(view, 0);
    }
}