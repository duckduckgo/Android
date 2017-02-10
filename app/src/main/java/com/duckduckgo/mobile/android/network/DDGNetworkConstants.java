package com.duckduckgo.mobile.android.network;

import android.app.Application;
import android.webkit.WebView;
import ch.boye.httpclientandroidlib.conn.ClientConnectionManager;
import ch.boye.httpclientandroidlib.conn.params.ConnManagerParams;
import ch.boye.httpclientandroidlib.conn.params.ConnPerRouteBean;
import ch.boye.httpclientandroidlib.conn.params.ConnRoutePNames;
import ch.boye.httpclientandroidlib.impl.conn.tsccm.ThreadSafeClientConnManager;
import ch.boye.httpclientandroidlib.params.BasicHttpParams;
import ch.boye.httpclientandroidlib.params.HttpParams;

import com.duckduckgo.mobile.android.DDGApplication;
import com.duckduckgo.mobile.android.util.PreferencesManager;

public class DDGNetworkConstants {
	public static DDGHttpClient mainClient;
	private static ClientConnectionManager mainConnManager;
    private static HttpParams httpParams = new BasicHttpParams();
    public final static String PROXY_HOST = "127.0.0.1";
    public final static int PROXY_HTTP_PORT = 8118; // default for Orbot/Tor
    private static WebView webView;

//    public static Map<String, String> extraHeaders = new HashMap<String, String>();

	public static void initialize(DDGApplication application){
        initializeMainClient(application, PreferencesManager.getEnableTor());
	}

    public static void setWebView(WebView webView){
        DDGNetworkConstants.webView = webView;
    }

    public static WebView getWebView(){
        return webView;
    }


    public static void initializeMainClient(Application application, boolean enableTor){
        // Create and initialize HTTP parameters
        httpParams = new BasicHttpParams();
        ConnManagerParams.setMaxTotalConnections(httpParams, 100);
        // Increase default max connection per route to 20
        ConnManagerParams.setMaxConnectionsPerRoute(httpParams, new ConnPerRouteBean(10));

        // Create an HttpClient with the ThreadSafeClientConnManager.
        // This connection manager must be used if more than one thread will
        // be using the HttpClient.
        mainConnManager = new ThreadSafeClientConnManager();
        mainClient = new DDGHttpClient(application.getApplicationContext(), mainConnManager, httpParams);
        if(enableTor){
            //mainClient.getStrongTrustManager().setNotifyVerificationFail(true);
            //mainClient.getStrongTrustManager().setNotifyVerificationSuccess(true);
            mainClient.useProxy(true, "http", PROXY_HOST, PROXY_HTTP_PORT);
        }
    }
}
