package com.duckduckgo.mobile.android.views.webview;

import java.util.HashSet;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebBackForwardList;
import android.webkit.WebHistoryItem;
import android.webkit.WebView;

import com.duckduckgo.mobile.android.bus.BusProvider;
import com.duckduckgo.mobile.android.events.RemoveWebFragmentEvent;
import com.duckduckgo.mobile.android.events.StopActionEvent;
import com.duckduckgo.mobile.android.util.DDGConstants;
import com.duckduckgo.mobile.android.util.DDGControlVar;
import com.duckduckgo.mobile.android.util.PreferencesManager;

public class DDGWebView extends WebView {

    public static final String ABOUT_BLANK = "about:blank";
	
	private DDGWebViewClient webViewClient = null;
	private DDGWebChromeClient webChromeClient = null;
	
	public boolean shouldClearHistory = false;

	public String mWebViewDefaultUA = null;
	
	public DDGWebView(Context context, AttributeSet attrs) {
		super(context, attrs);
		attrSet = attrs;
		mWebViewDefaultUA = getSettings().getUserAgentString();
	}

	public boolean is_gone=true;
	public AttributeSet attrSet = null;
	
	public void setWebViewClient(DDGWebViewClient client) {
		webViewClient = client;
		super.setWebViewClient(client);
	}
	
	public DDGWebViewClient getWebViewClient() {
		return webViewClient;
	}

	public void setWebChromeClient(DDGWebChromeClient client) {
		webChromeClient = client;
		super.setWebChromeClient(client);
	}

	public DDGWebChromeClient getWebChromeClient() {
		return webChromeClient;
	}
	
	public void onWindowVisibilityChanged(int visibility)
	       {super.onWindowVisibilityChanged(visibility);
	        if (visibility==View.GONE)
	           {try
	                {WebView.class.getMethod("onPause").invoke(this);//stop flash
	                }
	            catch (Exception e) {}
	            this.pauseTimers();
	            this.is_gone=true;
	           }
	        else if (visibility==View.VISIBLE)
	             {try
	                  {WebView.class.getMethod("onResume").invoke(this);//resume flash
	                  }
	              catch (Exception e) {}
	              this.resumeTimers();
	              this.is_gone=false;
	             }
	       }
	
	public AttributeSet getAttributes() {
		return attrSet;
	}
	
	public void stopView() {
		try
        {WebView.class.getMethod("onPause").invoke(this);//stop flash
        }
	    catch (Exception e) {}
	    this.pauseTimers();
	}
	
	public void resumeView() {
		try
        {WebView.class.getMethod("onResume").invoke(this);//resume flash
        }
	    catch (Exception e) {}
	    this.resumeTimers();
	}

    public void setUserAgentString(String url) {
        if(url.contains("duckduckgo.com")) {
            getSettings().setUserAgentString(DDGConstants.USER_AGENT);
        } else {
            getSettings().setUserAgentString(mWebViewDefaultUA);
        }
    }

    @Override
    public void loadUrl(String url) {
		if(url==null) {
            return;
        }
        setUserAgentString(url);
        super.loadUrl(url);
    }
	
	public void clearBrowserState() {		
		stopLoading();
		clearHistory();
        clearViewReliably();
        shouldClearHistory = true;
		
		//clearReadabilityState();
	}

    /**
     * The clearView method was deprecated in API level 18. Use this instead
     * See https://developer.android.com/reference/android/webkit/WebView.html#clearView%28%29
     */
    private void clearViewReliably() {
        loadUrl(ABOUT_BLANK);
    }
	
	public void backPressAction(boolean toHomeScreen) {
		WebBackForwardList history = copyBackForwardList();
		int lastIndex = history.getCurrentIndex();

        if(lastIndex > 0) {
			WebHistoryItem prevItem = history.getItemAtIndex(lastIndex-1);

            if(webChromeClient.isVideoFullscreen()) {
				hideCustomView();
			} else if(prevItem != null) {
				String prevUrl = prevItem.getUrl();
                if(ABOUT_BLANK.equals(prevUrl)){
                    goBackOrForward(-2);
                    if(lastIndex > 0){
                        if(toHomeScreen)
						BusProvider.getInstance().post(new RemoveWebFragmentEvent());
                    }
                    return;
                }
				goBack();
			}
			else {
				goBack();
			}
		}
		else if(toHomeScreen) {
			BusProvider.getInstance().post(new RemoveWebFragmentEvent());
			BusProvider.getInstance().post(new StopActionEvent());
		}
	}

    public void forwardPressAction() {
        WebBackForwardList history = copyBackForwardList();
        int lastIndex = history.getCurrentIndex();

        if(lastIndex < history.getSize()) {
            WebHistoryItem nextItem = history.getItemAtIndex(lastIndex+1);

            if(nextItem!=null) {
                String nextUrl = nextItem.getUrl();
                if(ABOUT_BLANK.equals(nextUrl)){
                    goBackOrForward(2);
                } else {
                    goForward();
                }
            }
        }
    }

    public static void clearCookies() {
        CookieManager.getInstance().removeAllCookie();
    }

    public static void recordCookies(boolean newState) {
        CookieManager.getInstance().setAcceptCookie(newState);
    }

    public static boolean hasCookies() {
        return CookieManager.getInstance().hasCookies();
    }

    public static boolean isRecordingCookies() {
        return CookieManager.getInstance().acceptCookie();
    }

    public void clearCache() {
        clearCache(true);
    }

	public void hideCustomView() {
		webChromeClient.onHideCustomView();
	}

	public boolean isVideoFullscreen() {
        return webChromeClient.isVideoFullscreen();
	}

}
