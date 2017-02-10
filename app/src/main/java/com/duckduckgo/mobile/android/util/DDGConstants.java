package com.duckduckgo.mobile.android.util;

import android.graphics.Typeface;

public class DDGConstants {
	public static String USER_AGENT = "DDG-Android-%version";

	public static final String AUTO_COMPLETE_URL = "https://duckduckgo.com/ac/?q=";
	public static final String SEARCH_URL = "https://www.duckduckgo.com/?ko=-1&q=";
    public static final String SEARCH_URL_JAVASCRIPT_DISABLED = "https://duckduckgo.com/html/?q=";
	public static final String SEARCH_URL_ONION = "http://3g2upl4pq6kufc4m.onion/?ko=-1&q=";

	public static final String SOURCE_JSON_PATH = "source.json";

    public static final int ALWAYS_INTERNAL = 0;
    public static final int ALWAYS_EXTERNAL = 1;

    public static final int CONFIRM_CLEAR_COOKIES = 200;
    public static final int CONFIRM_CLEAR_WEB_CACHE = 300;
}
