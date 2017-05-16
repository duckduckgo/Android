package com.duckduckgo.mobile.android.duckduckgo.util;

import android.content.Context;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

/**
 * Created by fgei on 5/15/17.
 */

public class KeyboardHelper {

    public static void hideKeyboard(View view) {
        InputMethodManager inputMethodManager = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    public static void showKeyboard() {

    }

    public static void hideKeyboardDelayed() {

    }

    public static void showKeyboardDelayed() {

    }
}
