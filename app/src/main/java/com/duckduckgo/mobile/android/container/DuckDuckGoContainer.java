package com.duckduckgo.mobile.android.container;

import android.graphics.drawable.Drawable;

import com.duckduckgo.mobile.android.adapters.AutoCompleteResultsAdapter;
import com.duckduckgo.mobile.android.util.SCREEN;
import com.duckduckgo.mobile.android.util.SESSIONTYPE;
import com.duckduckgo.mobile.android.util.TorIntegration;

public class DuckDuckGoContainer {
	
	public boolean webviewShowing = false;
	
	public SESSIONTYPE sessionType = SESSIONTYPE.SESSION_BROWSE;
	public String currentFragmentTag = "";
    public String prevFragmentTag = "";
    public String currentUrl = "";
	
	public SCREEN currentScreen = SCREEN.SCR_WEBVIEW;//SCREEN.SCR_STORIES;
	public SCREEN prevScreen = SCREEN.SCR_WEBVIEW;//SCREEN.SCR_STORIES;
	
	public Drawable /*progressDrawable, */searchFieldDrawable;
	public Drawable stopDrawable;

    public AutoCompleteResultsAdapter acAdapter = null;

    public TorIntegration torIntegration = null;
}
