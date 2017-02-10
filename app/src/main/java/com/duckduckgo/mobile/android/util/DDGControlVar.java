package com.duckduckgo.mobile.android.util;

import com.duckduckgo.mobile.android.container.DuckDuckGoContainer;

import java.util.HashSet;
import java.util.Set;

/**
 * This class contains public objects and primitives accessed and modified all throughout the app.
 */
public class DDGControlVar {
	
	public static SCREEN START_SCREEN = SCREEN.SCR_WEBVIEW;//SCR_SEARCH_HOME_PAGE;

	public static String regionString = "wt-wt";	// world traveler (none) as default
	
	public static boolean homeScreenShowing = true;

    public static int useExternalBrowser = DDGConstants.ALWAYS_INTERNAL;
	public static boolean isAutocompleteActive = true;

	public static DuckDuckGoContainer mDuckDuckGoContainer;

	public static boolean mCleanSearchBar = false;
	
	public static final Object DECODE_LOCK = new Object();
}
