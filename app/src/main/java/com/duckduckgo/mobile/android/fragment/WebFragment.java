package com.duckduckgo.mobile.android.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.view.menu.MenuBuilder;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.DownloadListener;

import com.duckduckgo.mobile.android.R;
import com.duckduckgo.mobile.android.actionbar.DDGActionBarManager;
import com.duckduckgo.mobile.android.activity.KeyboardService;
import com.duckduckgo.mobile.android.bus.BusProvider;
import com.duckduckgo.mobile.android.dialogs.menuDialogs.WebViewQueryMenuDialog;
import com.duckduckgo.mobile.android.dialogs.menuDialogs.WebViewWebPageMenuDialog;
import com.duckduckgo.mobile.android.download.ContentDownloader;
import com.duckduckgo.mobile.android.events.HandleShareButtonClickEvent;
import com.duckduckgo.mobile.android.events.OverflowButtonClickEvent;
import com.duckduckgo.mobile.android.events.WebViewEvents.WebViewBackPressActionEvent;
import com.duckduckgo.mobile.android.events.WebViewEvents.WebViewClearBrowserStateEvent;
import com.duckduckgo.mobile.android.events.WebViewEvents.WebViewClearCacheEvent;
import com.duckduckgo.mobile.android.events.WebViewEvents.WebViewOnPageStarted;
import com.duckduckgo.mobile.android.events.WebViewEvents.WebViewUpdateMenuNavigationEvent;
import com.duckduckgo.mobile.android.events.WebViewEvents.WebViewItemMenuClickEvent;
import com.duckduckgo.mobile.android.events.WebViewEvents.WebViewOpenMenuEvent;
import com.duckduckgo.mobile.android.events.WebViewEvents.WebViewReloadActionEvent;
import com.duckduckgo.mobile.android.events.WebViewEvents.WebViewSearchOrGoToUrlEvent;
import com.duckduckgo.mobile.android.events.WebViewEvents.WebViewSearchWebTermEvent;
import com.duckduckgo.mobile.android.events.shareEvents.ShareSearchEvent;
import com.duckduckgo.mobile.android.events.shareEvents.ShareWebPageEvent;
import com.duckduckgo.mobile.android.network.DDGNetworkConstants;
import com.duckduckgo.mobile.android.util.DDGConstants;
import com.duckduckgo.mobile.android.util.DDGControlVar;
import com.duckduckgo.mobile.android.util.DDGUtils;
import com.duckduckgo.mobile.android.util.PreferencesManager;
import com.duckduckgo.mobile.android.util.SESSIONTYPE;
import com.duckduckgo.mobile.android.util.URLTYPE;
import com.duckduckgo.mobile.android.views.DDGOverflowMenu;
import com.duckduckgo.mobile.android.views.webview.DDGWebChromeClient;
import com.duckduckgo.mobile.android.views.webview.DDGWebView;
import com.duckduckgo.mobile.android.views.webview.DDGWebViewClient;
import com.squareup.otto.Subscribe;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;

public class WebFragment extends Fragment {

	public static final String TAG = "web_fragment";
	public static final String URL = "url";
    public static final String SESSION_TYPE = "session_type";

    private Context context = null;

	private String mWebViewDefaultUA = null;
	private DDGWebView mainWebView = null;
	private ContentDownloader contentDownloader;
	private KeyboardService keyboardService;
	private View fragmentView;

	private boolean savedState = false;
	private URLTYPE urlType = URLTYPE.WEBPAGE;

    private Menu webMenu = null;
    private Menu headerMenu = null;
    private Menu mainMenu = null;
    private DDGOverflowMenu overflowMenu = null;

    public static WebFragment newInstance(String url, SESSIONTYPE sessionType) {
        WebFragment fragment = new WebFragment();
        Bundle args = new Bundle();
        args.putString(URL, url);
        args.putInt(SESSION_TYPE, sessionType.getCode());
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        this.context = context;
    }

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		if(savedInstanceState!=null) {
			savedState = true;
		}
		fragmentView = inflater.inflate(R.layout.fragment_web, container, false);
		return fragmentView;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		setRetainInstance(true);
        context = getActivity();
        init();

		// Restore the state of the WebView
		if(savedInstanceState!=null) {
			mainWebView.restoreState(savedInstanceState);
			urlType = URLTYPE.getByCode(savedInstanceState.getInt("url_type"));
		}
	}

    @Override
    public void onStart() {
        super.onStart();
		BusProvider.getInstance().register(this);
        DDGControlVar.mDuckDuckGoContainer.torIntegration.prepareTorSettings();
    }

    @Override
    public void onResume() {
        super.onResume();
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			mainWebView.onResume();
		}
    }

    @Override
    public void onPause() {
		dismissMenu();
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			mainWebView.onPause();
		}
        super.onPause();
    }

	@Override
	public void onStop() {
		BusProvider.getInstance().unregister(this);
		super.onStop();
	}

	@Override
	public void onDestroy() {
		mainWebView.loadUrl(DDGWebView.ABOUT_BLANK);
		mainWebView.stopView();
		mainWebView.setWebViewClient(null);
		mainWebView.setWebChromeClient(null);
		mainWebView = null;
		super.onDestroy();
	}

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if(!hidden) {
            DDGActionBarManager.getInstance().setSearchBarText(mainWebView.getUrl());
            mainWebView.getSettings().setJavaScriptEnabled(PreferencesManager.getEnableJavascript());
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        dismissMenu();
    }

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt("url_type", urlType.getCode());
		// Save the state of the WebView
		mainWebView.saveState(outState);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        headerMenu = new MenuBuilder(context);
        inflater.inflate(R.menu.web_navigation, headerMenu);
		inflater.inflate(R.menu.feed, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        if(headerMenu!=null) {
            MenuItem backItem = headerMenu.findItem(R.id.action_back);
            MenuItem forwardItem = headerMenu.findItem(R.id.action_forward);
            backItem.setEnabled(mainWebView.canGoBack());
            forwardItem.setEnabled(mainWebView.canGoForward());
        }
        webMenu = menu;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
        HashMap<Integer, Boolean> newStates;
		switch(item.getItemId()) {
            case R.id.action_reload:
                actionReload();
                overflowMenu.dismiss();
                return true;
            case R.id.action_share:
				actionShare();
				return true;
            case R.id.action_back:
                mainWebView.backPressAction(false);
                newStates = new HashMap<Integer, Boolean>();
                newStates.put(R.id.action_back, mainWebView.canGoBack());
                newStates.put(R.id.action_forward, mainWebView.canGoForward());
                BusProvider.getInstance().post(new WebViewUpdateMenuNavigationEvent(newStates));
                return true;
            case R.id.action_forward:
                mainWebView.forwardPressAction();
                newStates = new HashMap<Integer, Boolean>();
                newStates.put(R.id.action_back, mainWebView.canGoBack());
                newStates.put(R.id.action_forward, mainWebView.canGoForward());
                BusProvider.getInstance().post(new WebViewUpdateMenuNavigationEvent(newStates));
                return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	public void init() {
		keyboardService = new KeyboardService(getActivity());
		mainWebView = (DDGWebView) fragmentView.findViewById(R.id.fragmentMainWebView);
		mainWebView.getSettings().setJavaScriptEnabled(PreferencesManager.getEnableJavascript());
        DDGWebView.recordCookies(PreferencesManager.getRecordCookies());
		DDGNetworkConstants.setWebView(mainWebView);

		// get default User-Agent string for reuse later
		mWebViewDefaultUA = mainWebView.getSettings().getUserAgentString();

		mainWebView.setWebViewClient(new DDGWebViewClient(getActivity(), this));

        View hideContent = getActivity().findViewById(R.id.main_container);
        ViewGroup showContent = (ViewGroup) getActivity().findViewById(R.id.fullscreen_video_container);
        mainWebView.setWebChromeClient(new DDGWebChromeClient(getActivity(), hideContent, showContent));

		contentDownloader = new ContentDownloader(getActivity());

		mainWebView.setDownloadListener(new DownloadListener() {
			public void onDownloadStart(String url, String userAgent,
										String contentDisposition, String mimetype,
										long contentLength) {

				contentDownloader.downloadContent(url, mimetype);
			}
		});

        //temporary fix until next appcompat release
        //https://code.google.com/p/android/issues/detail?id=80434
		//todo remove his
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB && Build.VERSION.SDK_INT <= Build.VERSION_CODES.HONEYCOMB_MR2) {
            mainWebView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    return true;
                }
            });
            mainWebView.setLongClickable(false);
        }

        webMenu = new MenuBuilder(getActivity());
        getActivity().getMenuInflater().inflate(R.menu.feed, webMenu);
        headerMenu = new MenuBuilder(getActivity());
        getActivity().getMenuInflater().inflate(R.menu.web_navigation, headerMenu);
        mainMenu = new MenuBuilder(getActivity());
        getActivity().getMenuInflater().inflate(R.menu.main, mainMenu);

        Bundle args = getArguments();

        if(args!=null) {
            String url = null;
            if(args.containsKey(URL)) url = args.getString(URL);
            SESSIONTYPE sessionType = SESSIONTYPE.SESSION_BROWSE;
            if(args.containsKey(SESSION_TYPE)) sessionType = SESSIONTYPE.getByCode(args.getInt(SESSION_TYPE));

            if(url!=null) {
                //searchOrGoToUrl(url, sessionType);
            }
        }
	}

	public boolean getSavedState() {
		return savedState;
	}

	public void searchOrGoToUrl(String text) {
		searchOrGoToUrl(text, SESSIONTYPE.SESSION_BROWSE);
	}

	public void searchOrGoToUrl(String text, SESSIONTYPE sessionType) {
        DDGControlVar.mCleanSearchBar = false;
		savedState = false;

		DDGControlVar.mDuckDuckGoContainer.sessionType = sessionType;

		if (text!=null && text.length() > 0) {
			java.net.URL searchAsUrl = null;
			String modifiedText = null;
			try {
				searchAsUrl = new URL(text);
				searchAsUrl.toURI();
			} catch (MalformedURLException e) {
				e.printStackTrace();
			} catch (URISyntaxException e) {
				e.printStackTrace();
				searchAsUrl = null;
			}

			if (searchAsUrl == null && !DDGUtils.isValidIpAddress(text)) {
				modifiedText = "http://" + text;
				try {
					searchAsUrl = new URL(modifiedText);
					searchAsUrl.toURI();
				} catch (MalformedURLException e) {
					e.printStackTrace();
				} catch (URISyntaxException e) {
					e.printStackTrace();
					searchAsUrl = null;
				}
			}

			//We use the . check to determine if this is a single word or not...
			//if it doesn't contain a . plus domain (2 more characters) it won't be a URL, even if it's valid, like http://test
			if (searchAsUrl != null) {
				if (modifiedText != null) {
					//Show the modified url text
					if (modifiedText.contains(".") && modifiedText.length() > (modifiedText.indexOf(".") + 2)) {
						showWebUrl(modifiedText);
					} else {
						searchWebTerm(text);
					}
				} else {
					if (text.contains(".") && text.length() > (text.indexOf(".") + 2)) {
						//Show the url text
						showWebUrl(text);
					} else {
						searchWebTerm(text);
					}
				}
			} else {
				searchWebTerm(text);
			}
		}
	}

	public void searchWebTerm(String term) {
		DDGControlVar.mDuckDuckGoContainer.sessionType = SESSIONTYPE.SESSION_SEARCH;

		if(DDGControlVar.useExternalBrowser == DDGConstants.ALWAYS_EXTERNAL) {
			DDGUtils.searchExternal(context, term);
			return;
		}

		urlType = URLTYPE.SERP;

		if(!savedState){
            String baseUrl;
			if(DDGControlVar.regionString.equals("wt-wt")){	// default
                if(PreferencesManager.getEnableJavascript()) {
                    baseUrl = DDGConstants.SEARCH_URL;
                } else {
                    baseUrl = DDGConstants.SEARCH_URL_JAVASCRIPT_DISABLED;
                }
                mainWebView.loadUrl(baseUrl + URLEncoder.encode(term));
			}
			else {
                if(PreferencesManager.getEnableJavascript()) {
                    baseUrl = DDGConstants.SEARCH_URL;
                } else {
                    baseUrl = DDGConstants.SEARCH_URL_JAVASCRIPT_DISABLED;
                }
                mainWebView.loadUrl(baseUrl + URLEncoder.encode(term) + "&kl=" + URLEncoder.encode(DDGControlVar.regionString));
			}
		}
	}

	public void showWebUrl(String url) {
		if(DDGControlVar.useExternalBrowser == DDGConstants.ALWAYS_EXTERNAL) {
			Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
			DDGUtils.execIntentIfSafe(context, browserIntent);
			return;
		}

		if(DDGUtils.isSerpUrl(url)) {
			urlType = URLTYPE.SERP;
		} else {
			urlType = URLTYPE.WEBPAGE;
		}

		if(!savedState) {
			mainWebView.loadUrl(url);
		}
	}

    public boolean hasActiveSession() {
        return mainWebView != null && mainWebView.copyBackForwardList().getSize() > 0;
    }

    public void clearWebView() {
        mainWebView.clearBrowserState();
    }

	public boolean canGoBack() {
        return mainWebView.canGoBack();
    }

	private void handleShareButtonClick() {
		String webViewUrl = mainWebView.getUrl();
		if(webViewUrl == null){
			webViewUrl = "";
		}

		if(DDGUtils.isSerpUrl(webViewUrl)) {
			new WebViewQueryMenuDialog(context, webViewUrl).show();
		}
		else {
			new WebViewWebPageMenuDialog(context, webViewUrl).show();
		}
	}

	private void actionShare() {
		String webViewUrl = mainWebView.getUrl();
		if(webViewUrl==null) {
			webViewUrl = "";
		}
		switch(urlType) {
			case SERP:
				BusProvider.getInstance().post(new ShareSearchEvent(webViewUrl));
				break;
			case WEBPAGE:
				BusProvider.getInstance().post(new ShareWebPageEvent(webViewUrl, webViewUrl));
				break;
			default:
				break;
		}
	}

	private void actionExternalBrowser() {
		String webViewUrl = mainWebView.getUrl();
		if(webViewUrl==null) {
			webViewUrl = "";
		}
		Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(webViewUrl));
		DDGUtils.execIntentIfSafe(getActivity(), browserIntent);
	}

	private void actionReload() {
		mainWebView.reload();
	}

    public void setContext(Context context) {
       if(this.context==null) {
           this.context = context;
       }
    }

    private void dismissMenu() {
        if(overflowMenu!=null && overflowMenu.isShowing()) {
            overflowMenu.dismiss();
        }
    }

	@Subscribe
	public void onWebViewClearBrowserStateEvent(WebViewClearBrowserStateEvent event) {
        mainWebView.clearBrowserState();
	}

	@Subscribe
	public void onWebViewClearCacheEvent(WebViewClearCacheEvent event) {
		mainWebView.clearCache();
	}

	@Subscribe
	public void onWebViewReloadActionEvent(WebViewReloadActionEvent event) {
		actionReload();
	}

	@Subscribe
	public void onWebViewBackPressActionEvent(WebViewBackPressActionEvent event) {
		mainWebView.backPressAction(true);
	}

	@Subscribe
	public void onWebViewSearchOrGoToUrlEvent(WebViewSearchOrGoToUrlEvent event) {
		searchOrGoToUrl(event.text, event.sessionType);
	}

	@Subscribe
	public void onWebViewSearchWebTermEvent(WebViewSearchWebTermEvent event) {
		searchWebTerm(event.term);
	}

	@Subscribe
	public void onHandleShareButtonClickEvent(HandleShareButtonClickEvent event) {
		handleShareButtonClick();
	}

    @Subscribe
    public void onWebViewItemMenuClickEvent(WebViewItemMenuClickEvent event) {
        onOptionsItemSelected(event.item);
    }

    @Subscribe
    public void onWebViewOpenMenuEvent(WebViewOpenMenuEvent event) {
        if(webMenu!=null) {

            onPrepareOptionsMenu(webMenu);

            if(overflowMenu!=null && overflowMenu.isShowing()) {
                return;
            }

            overflowMenu = new DDGOverflowMenu(getActivity());
            overflowMenu.setHeaderMenu(headerMenu);
            overflowMenu.setMenu(webMenu);
            overflowMenu.setMenu(mainMenu, true);
            overflowMenu.show(event.anchorView);

        }
    }

    @Subscribe
    public void onOverflowButtonClickEvent(OverflowButtonClickEvent event) {
        if(DDGControlVar.mDuckDuckGoContainer.currentFragmentTag.equals(getTag()) && webMenu!=null) {
            if(overflowMenu!=null && overflowMenu.isShowing()) {
                return;
            }

            onPrepareOptionsMenu(webMenu);

            overflowMenu = new DDGOverflowMenu(getActivity());
            overflowMenu.setHeaderMenu(headerMenu);
            overflowMenu.setMenu(webMenu);
            overflowMenu.setMenu(mainMenu, true);
            overflowMenu.show(event.anchor);
        }
    }

	@Subscribe
	public void onWebViewOnPageStarted(WebViewOnPageStarted event) {
		if(DDGUtils.isSerpUrl(event.url)) {
			urlType = URLTYPE.SERP;
		} else {
			urlType = URLTYPE.WEBPAGE;
		}
	}

}
