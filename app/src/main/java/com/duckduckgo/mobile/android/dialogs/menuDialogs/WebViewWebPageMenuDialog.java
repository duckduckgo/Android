package com.duckduckgo.mobile.android.dialogs.menuDialogs;

import android.app.AlertDialog;
import android.content.Context;

import com.duckduckgo.mobile.android.R;
import com.duckduckgo.mobile.android.adapters.PageMenuContextAdapter;
import com.duckduckgo.mobile.android.adapters.menuAdapters.WebViewWebPageMenuAdapter;
import com.duckduckgo.mobile.android.listener.ExecuteActionOnClickListener;


/*
Shows a dialog to alert the user the feedrequest failed, asking him to try again.
 */
public final class WebViewWebPageMenuDialog extends AlertDialog.Builder{
	public WebViewWebPageMenuDialog(final Context context, String webViewUrl) {
		super(context);

        final PageMenuContextAdapter contextAdapter = new WebViewWebPageMenuAdapter(context, android.R.layout.select_dialog_item, android.R.id.text1, webViewUrl);

        setTitle(R.string.PageOptionsTitle);
        setAdapter(contextAdapter, new ExecuteActionOnClickListener(contextAdapter));
	}
}
