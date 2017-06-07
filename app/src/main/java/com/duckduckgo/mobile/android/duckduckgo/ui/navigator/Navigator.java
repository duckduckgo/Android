package com.duckduckgo.mobile.android.duckduckgo.ui.navigator;

import android.content.Context;
import android.support.v4.app.Fragment;

import com.duckduckgo.mobile.android.duckduckgo.ui.browser.tab.Tab;
import com.duckduckgo.mobile.android.duckduckgo.ui.tabswitcher.TabSwitcherActivity;

import java.util.List;

/**
 * Created by fgei on 5/29/17.
 */

public class Navigator {

    public static void navigateToTabSwitcher(Context context, Fragment fragment, int requestCode, List<Tab> extraTabs) {
        fragment.startActivityForResult(TabSwitcherActivity.getStartIntent(context, extraTabs), requestCode);
    }
}
