package com.duckduckgo.mobile.android.activity;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebStorage;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.duckduckgo.mobile.android.DDGApplication;
import com.duckduckgo.mobile.android.R;
import com.duckduckgo.mobile.android.actionbar.DDGActionBarManager;
import com.duckduckgo.mobile.android.adapters.AutoCompleteResultsAdapter;
import com.duckduckgo.mobile.android.bus.BusProvider;
import com.duckduckgo.mobile.android.container.DuckDuckGoContainer;
import com.duckduckgo.mobile.android.events.AutoCompleteResultClickEvent;
import com.duckduckgo.mobile.android.events.ConfirmDialogOkEvent;
import com.duckduckgo.mobile.android.events.DisplayHomeScreenEvent;
import com.duckduckgo.mobile.android.events.DisplayScreenEvent;
import com.duckduckgo.mobile.android.events.ReloadEvent;
import com.duckduckgo.mobile.android.events.RemoveWebFragmentEvent;
import com.duckduckgo.mobile.android.events.RequestOpenWebPageEvent;
import com.duckduckgo.mobile.android.events.ShowAutoCompleteResultsEvent;
import com.duckduckgo.mobile.android.events.StopActionEvent;
import com.duckduckgo.mobile.android.events.WebViewEvents.WebViewBackPressActionEvent;
import com.duckduckgo.mobile.android.events.WebViewEvents.WebViewClearCacheEvent;
import com.duckduckgo.mobile.android.events.WebViewEvents.WebViewItemMenuClickEvent;
import com.duckduckgo.mobile.android.events.WebViewEvents.WebViewOpenMenuEvent;
import com.duckduckgo.mobile.android.events.WebViewEvents.WebViewReloadActionEvent;
import com.duckduckgo.mobile.android.events.WebViewEvents.WebViewSearchOrGoToUrlEvent;
import com.duckduckgo.mobile.android.events.WebViewEvents.WebViewSearchWebTermEvent;
import com.duckduckgo.mobile.android.events.externalEvents.SearchExternalEvent;
import com.duckduckgo.mobile.android.events.externalEvents.SendToExternalBrowserEvent;
import com.duckduckgo.mobile.android.events.pasteEvents.SuggestionPasteEvent;
import com.duckduckgo.mobile.android.events.shareEvents.ShareSearchEvent;
import com.duckduckgo.mobile.android.events.shareEvents.ShareWebPageEvent;
import com.duckduckgo.mobile.android.fragment.AboutFragment;
import com.duckduckgo.mobile.android.fragment.HelpFeedbackFragment;
import com.duckduckgo.mobile.android.fragment.PrefFragment;
import com.duckduckgo.mobile.android.fragment.SearchFragment;
import com.duckduckgo.mobile.android.fragment.WebFragment;
import com.duckduckgo.mobile.android.objects.SuggestObject;
import com.duckduckgo.mobile.android.util.AppStateManager;
import com.duckduckgo.mobile.android.util.DDGConstants;
import com.duckduckgo.mobile.android.util.DDGControlVar;
import com.duckduckgo.mobile.android.util.DDGUtils;
import com.duckduckgo.mobile.android.util.DisplayStats;
import com.duckduckgo.mobile.android.util.PreferencesManager;
import com.duckduckgo.mobile.android.util.SCREEN;
import com.duckduckgo.mobile.android.util.SESSIONTYPE;
import com.duckduckgo.mobile.android.util.Sharer;
import com.duckduckgo.mobile.android.util.SuggestType;
import com.duckduckgo.mobile.android.util.TorIntegration;
import com.duckduckgo.mobile.android.views.autocomplete.BackButtonPressedEventListener;
import com.duckduckgo.mobile.android.views.autocomplete.DDGAutoCompleteTextView;
import com.duckduckgo.mobile.android.views.webview.DDGWebView;
import com.squareup.otto.Subscribe;

import java.util.List;

public class DuckDuckGo extends AppCompatActivity {
	protected final String TAG = "DuckDuckGo";
    private KeyboardService keyboardService;
    private FrameLayout fragmentContainer;

	private FragmentManager fragmentManager;

    private Toolbar toolbar;
	private DDGAutoCompleteTextView searchEditText;

	private SharedPreferences sharedPreferences;
		
	public boolean savedState = false;
    private boolean canCommitFragmentSafely = true;
    private boolean newIntent = false;
		
	private final int PREFERENCES_RESULT = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "on create");
        canCommitFragmentSafely = true;

        keyboardService = new KeyboardService(this);

        sharedPreferences = DDGApplication.getSharedPreferences();

		setContentView(R.layout.main);
        getWindow().getDecorView().setBackgroundColor(getResources().getColor(R.color.background));
        
        DDGUtils.displayStats = new DisplayStats(this);

        //Dynamic Search Hint based on screen width
        //Get Search box
        searchEditText = (DDGAutoCompleteTextView) findViewById(R.id.searchEditText);
        //Get screen width in pixels
        DisplayMetrics displaymetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        //Set Search Hint
        if (displaymetrics.widthPixels < 480)   //Minimum size for full display
            searchEditText.setHint(R.string.SearchStringSmall); //DDG
        else
            searchEditText.setHint(R.string.SearchStringBig);   //DuckDuckGo

        if(savedInstanceState != null)
        	savedState = true;

        DDGControlVar.isAutocompleteActive = PreferencesManager.getAutocomplete();
        DDGControlVar.mDuckDuckGoContainer = (DuckDuckGoContainer) getLastCustomNonConfigurationInstance();
    	if(DDGControlVar.mDuckDuckGoContainer == null){
            initializeContainer();
    	}

		fragmentContainer = (FrameLayout) findViewById(R.id.fragmentContainer);

        toolbar = (Toolbar) findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);
        DDGActionBarManager.getInstance().init(this, this, toolbar);

        initSearchField();

		fragmentManager = getSupportFragmentManager();

        fragmentManager.addOnBackStackChangedListener(new FragmentManager.OnBackStackChangedListener() {
            @Override
            public void onBackStackChanged() {
                Log.d(TAG, "Fragment Back Stack count: " + fragmentManager.getBackStackEntryCount());
                showAllFragments();
                if (fragmentManager.getBackStackEntryCount() > 0) {
                    String tag = fragmentManager.getBackStackEntryAt(fragmentManager.getBackStackEntryCount() - 1).getName();
                    if (tag != null) {
                        if(!DDGControlVar.mDuckDuckGoContainer.currentFragmentTag.equals(tag)) {
                            DDGControlVar.mDuckDuckGoContainer.prevFragmentTag = DDGControlVar.mDuckDuckGoContainer.currentFragmentTag;
                        }
                        DDGControlVar.mDuckDuckGoContainer.currentFragmentTag = tag;
                        if (!tag.equals(WebFragment.TAG) && !DDGControlVar.mDuckDuckGoContainer.webviewShowing) {
                            DDGControlVar.mDuckDuckGoContainer.prevScreen = DDGControlVar.mDuckDuckGoContainer.currentScreen;
                        }
                        DDGControlVar.mDuckDuckGoContainer.currentScreen = DDGUtils.getScreenByTag(tag);
                        DDGControlVar.mDuckDuckGoContainer.webviewShowing = tag.equals(WebFragment.TAG);
                        DDGControlVar.homeScreenShowing = DDGControlVar.mDuckDuckGoContainer.currentScreen == DDGControlVar.START_SCREEN;

                        DDGActionBarManager.getInstance().updateActionBar(tag);
                    }
                    Log.e(TAG, "Fragment Back Stack current tag: " + DDGControlVar.mDuckDuckGoContainer.currentFragmentTag);
                    showAllFragments();
                }
            }
        });

		if(savedInstanceState==null) {
			displayHomeScreen();
            keyboardService.showKeyboard(getSearchField());
        }

        // global search intent
        Intent intent = getIntent();
        processIntent(intent);
    }

    private void initSearchField() {
        getSearchField().setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent event) {
                if(textView == getSearchField() && actionId != EditorInfo.IME_NULL) {
                    if(getSearchField().getTrimmedText()!=null && getSearchField().getTrimmedText().length()!=0) {
                        searchOrGoToUrl(getSearchField().getTrimmedText());

                        if(DDGControlVar.useExternalBrowser == DDGConstants.ALWAYS_EXTERNAL && !PreferencesManager.getRecordHistory()) {
                            DDGActionBarManager.getInstance().clearSearchBar();
                        }
                    }
                    keyboardService.hideKeyboard(textView);
                    return true;
                }
                return false;
            }
        });

        getSearchField().setOnBackButtonPressedEventListener(new BackButtonPressedEventListener() {
            @Override
            public void onBackButtonPressed() {
                onBackPressed();
            }
        });

        // This makes a little (X) to clear the search bar.
        //DDGControlVar.mDuckDuckGoContainer.stopDrawable.setBounds(0, 0, (int)Math.floor(DDGControlVar.mDuckDuckGoContainer.stopDrawable.getIntrinsicWidth()/1.5), (int)Math.floor(DDGControlVar.mDuckDuckGoContainer.stopDrawable.getIntrinsicHeight()/1.5));
        DDGControlVar.mDuckDuckGoContainer.stopDrawable.setBounds(0, 0, (int)Math.floor(DDGControlVar.mDuckDuckGoContainer.stopDrawable.getIntrinsicWidth()), (int)Math.floor(DDGControlVar.mDuckDuckGoContainer.stopDrawable.getIntrinsicHeight()));
        getSearchField().setCompoundDrawables(null, null, getSearchField().getText().toString().equals("") ? null : DDGControlVar.mDuckDuckGoContainer.stopDrawable, null);

        getSearchField().setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    DDGControlVar.mCleanSearchBar = true;
                }

                if (getSearchField().getCompoundDrawables()[2] == null) {
                    return false;
                }
                if (event.getAction() != MotionEvent.ACTION_UP) {
                    return false;
                }
                if (event.getX() > getSearchField().getWidth() - getSearchField().getPaddingRight() - DDGControlVar.mDuckDuckGoContainer.stopDrawable.getIntrinsicWidth()) {
                    if(getSearchField().getCompoundDrawables()[2] == DDGControlVar.mDuckDuckGoContainer.stopDrawable) {
                        stopAction();
                    }
                    else {
                        reloadAction();
                    }
                }
                return false;
            }

        });

        getSearchField().addTextChangedListener(new TextWatcher() {
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                getSearchField().setCompoundDrawables(null, null, getSearchField().getText().toString().equals("") ? null : DDGControlVar.mDuckDuckGoContainer.stopDrawable, null);

                if(DDGControlVar.isAutocompleteActive) {
                    DDGControlVar.mDuckDuckGoContainer.acAdapter.getFilter().filter(s);
                }
            }

            public void afterTextChanged(Editable arg0) {
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
        });
    }


    private void initializeContainer() {
        DDGControlVar.mDuckDuckGoContainer = new DuckDuckGoContainer();

        DDGControlVar.mDuckDuckGoContainer.webviewShowing = false;
        DDGControlVar.mDuckDuckGoContainer.currentScreen = DDGControlVar.START_SCREEN;
        DDGControlVar.mDuckDuckGoContainer.currentFragmentTag = DDGUtils.getTagByScreen(DDGControlVar.mDuckDuckGoContainer.currentScreen);
        DDGControlVar.mDuckDuckGoContainer.prevScreen = DDGControlVar.mDuckDuckGoContainer.currentScreen;

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            DDGControlVar.mDuckDuckGoContainer.stopDrawable = DuckDuckGo.this.getResources().getDrawable(R.drawable.cross, getTheme());
            DDGControlVar.mDuckDuckGoContainer.searchFieldDrawable = DuckDuckGo.this.getResources().getDrawable(R.drawable.searchfield, getTheme());
        } else {
            DDGControlVar.mDuckDuckGoContainer.stopDrawable = DuckDuckGo.this.getResources().getDrawable(R.drawable.cross);
            DDGControlVar.mDuckDuckGoContainer.searchFieldDrawable = DuckDuckGo.this.getResources().getDrawable(R.drawable.searchfield);
        }
        DDGControlVar.mDuckDuckGoContainer.searchFieldDrawable.setAlpha(150);

        DDGControlVar.mDuckDuckGoContainer.acAdapter = new AutoCompleteResultsAdapter(this);

        DDGControlVar.mDuckDuckGoContainer.torIntegration = new TorIntegration(this);
    }

    public void showAllFragments() {
        Log.d(TAG, "show all fragments");
        if(fragmentManager.getFragments()!=null && fragmentManager.getFragments().size()!=0) {
            for (Fragment fragment : fragmentManager.getFragments()) {
                if(fragment!=null) {
                    Log.d(TAG, "fragment: " + fragment.getTag() + " - visible: " + fragment.isVisible());
                }
            }
        }
    }
	
	/**
	 * Displays given screen (stories, saved, settings etc.)
	 * 
	 * @param screenToDisplay Screen to display
	 * @param clean Whether screen state (searchbar, browser etc.) states will get cleaned
     * @param displayHomeScreen Whether to display home screen
	 */
	public void displayScreen(SCREEN screenToDisplay, boolean clean, boolean displayHomeScreen) {
        Log.d(TAG, "display screen: "+screenToDisplay);

        Fragment fragment = null;
        String tag = "";

		switch(screenToDisplay) {
            case SCR_WEBVIEW:
                fragment = new WebFragment();
                tag = WebFragment.TAG;
                break;
            case SCR_ABOUT:

                fragment = new AboutFragment();
                tag = AboutFragment.TAG;
                break;
            case SCR_HELP:
                DDGActionBarManager.getInstance().resetScreenState();
                fragment = new HelpFeedbackFragment();
                tag = HelpFeedbackFragment.TAG;
                break;
            case SCR_SETTINGS:
                fragment = new PrefFragment();
                tag = PrefFragment.TAG;
                break;
            default:
				break;
		}

        if(!tag.equals("")) {
            changeFragment(fragment, tag, displayHomeScreen);
        }
	}

    public void displayScreen(SCREEN screenToDisplay, boolean clean) {
        displayScreen(screenToDisplay, clean, false);
    }

    public void displayFirstWebScreen(String url, SESSIONTYPE sessionType) {
        if(url==null || url.length()<1) {
            return;
        }
        if(sessionType==null) sessionType = SESSIONTYPE.SESSION_BROWSE;
        Fragment fragment = WebFragment.newInstance(url, sessionType);
        String tag = WebFragment.TAG;
        changeFragment(fragment, tag);
    }
	
	private void displayHomeScreen() {
        Log.d(TAG, "display home screen");

        DDGControlVar.mDuckDuckGoContainer.currentUrl = "";
        displayScreen(DDGControlVar.START_SCREEN, true, true);
        DDGControlVar.mDuckDuckGoContainer.sessionType = SESSIONTYPE.SESSION_BROWSE;
	}

    private void processIntent(Intent intent) {
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            intent.setAction(Intent.ACTION_MAIN);
            String query = intent.getStringExtra(SearchManager.QUERY);
            DDGActionBarManager.getInstance().setSearchBarText(query);
            BusProvider.getInstance().post(new WebViewSearchWebTermEvent(query));
        }
        else if(intent.getBooleanExtra("widget", false)) {
            if(!getSearchField().getText().toString().equals("")) {
                DDGActionBarManager.getInstance().clearSearchBar();
            }
            WebFragment fragment = (WebFragment) fragmentManager.findFragmentByTag(WebFragment.TAG);
            if(fragment == null || !fragment.isVisible()) {
                displayScreen(SCREEN.SCR_WEBVIEW, true);
                boolean clearWebView = fragment.hasActiveSession();
                if(clearWebView) fragment.clearWebView();
            }
            keyboardService.showKeyboardDelayed(getSearchField());
        }
        else if(Intent.ACTION_VIEW.equals(intent.getAction())) {
            searchOrGoToUrl(intent.getDataString());
        }
        else if(Intent.ACTION_ASSIST.equals(intent.getAction())){
            if(!getSearchField().getText().toString().equals("")) {
                DDGActionBarManager.getInstance().clearSearchBar();
            }

            WebFragment fragment = (WebFragment) fragmentManager.findFragmentByTag(WebFragment.TAG);
            if(fragment == null || !fragment.isVisible()) {
                displayScreen(SCREEN.SCR_WEBVIEW, true);
                boolean clearWebView = fragment.hasActiveSession();
                if(clearWebView) fragment.clearWebView();
            }
            keyboardService.showKeyboardDelayed(getSearchField());
        }
        else if(DDGControlVar.mDuckDuckGoContainer.webviewShowing) {
            Fragment fragment = fragmentManager.findFragmentByTag(WebFragment.TAG);
            if(fragmentManager.findFragmentByTag(WebFragment.TAG)== null || !fragment.isVisible()) {
                displayScreen(SCREEN.SCR_WEBVIEW, false);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "on new intent: " + intent.toString());
        newIntent = true;
        setIntent(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        DDGControlVar.mDuckDuckGoContainer.torIntegration.prepareTorSettings();
        BusProvider.getInstance().register(this);
    }

	@Override
	public void onResume() {
		super.onResume();
        Log.d(TAG, "on resume");
		
        DDGUtils.displayStats.refreshStats(this);
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
        canCommitFragmentSafely = true;
        if(newIntent) {
            processIntent(getIntent());
            newIntent = false;
        }
    }

	@Override
	public void onPause() {
		super.onPause();
        Log.d(TAG, "on pause");
        canCommitFragmentSafely = false;

        DDGActionBarManager.getInstance().dismissMenu();
		
		// XXX keep these for low memory conditions
		AppStateManager.saveAppState(sharedPreferences, DDGControlVar.mDuckDuckGoContainer);
	}
	
	@Override
	protected void onStop() {
		super.onStop();
        BusProvider.getInstance().unregister(this);
        DDGControlVar.mDuckDuckGoContainer.torIntegration.dismissDialogs();
        Log.d(TAG, "on stop");
	}
	
	@Override
	public void onBackPressed() {
        WebFragment webFragment = (WebFragment) getSupportFragmentManager().findFragmentByTag(WebFragment.TAG);

        if(DDGControlVar.mDuckDuckGoContainer.currentScreen == SCREEN.SCR_WEBVIEW
                && webFragment != null
                && isFragmentVisible(WebFragment.TAG)) {
            if(webFragment.canGoBack()) {
                BusProvider.getInstance().post(new WebViewBackPressActionEvent());
            } else {
                finish();
            }
        }
        else if(fragmentManager.getBackStackEntryCount()==1) {
            finish();
        }
		else if(!isFinishing()) {
            super.onBackPressed();
		}
	}

    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {

        if(DDGControlVar.mDuckDuckGoContainer.webviewShowing && fragmentManager.findFragmentByTag(WebFragment.TAG)!=null && fragmentManager.findFragmentByTag(WebFragment.TAG).isVisible()) {
            if(openingMenu!=null) {
                openingMenu.close();
                BusProvider.getInstance().post(new WebViewOpenMenuEvent(toolbar));
            }
            return false;
        }
        return super.onMenuOpened(featureId, menu);
    }

    private Menu openingMenu = null;

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        this.openingMenu = menu;
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.action_help_feedback:
                actionHelpFeedback();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void actionHelpFeedback(){
        displayScreen(SCREEN.SCR_HELP, false);
    }

    private void actionSettings() {
        displayScreen(SCREEN.SCR_SETTINGS, false);
    }

	public void reloadAction() {
		DDGControlVar.mCleanSearchBar = false;
        DDGControlVar.mDuckDuckGoContainer.stopDrawable.setBounds(0, 0, (int) Math.floor(DDGControlVar.mDuckDuckGoContainer.stopDrawable.getIntrinsicWidth() / 1.5), (int) Math.floor(DDGControlVar.mDuckDuckGoContainer.stopDrawable.getIntrinsicHeight() / 1.5));
		getSearchField().setCompoundDrawables(null, null, getSearchField().getText().toString().equals("") ? null : DDGControlVar.mDuckDuckGoContainer.stopDrawable, null);

		BusProvider.getInstance().post(new WebViewReloadActionEvent());
	}

	private void stopAction() {
		DDGControlVar.mCleanSearchBar = true;
        DDGActionBarManager.getInstance().stopProgress();
    	getSearchField().setText("");

    	// This makes a little (X) to clear the search bar.
    	getSearchField().setCompoundDrawables(null, null, null, null);
	}

	public void searchOrGoToUrl(final String text, final SESSIONTYPE sessionType) {
        if(DDGControlVar.useExternalBrowser==DDGConstants.ALWAYS_INTERNAL) {
            if(fragmentManager.findFragmentByTag(WebFragment.TAG)==null) {
                displayFirstWebScreen(text, sessionType);
            } else {
                displayScreen(SCREEN.SCR_WEBVIEW, false);
                BusProvider.getInstance().post(new WebViewSearchOrGoToUrlEvent(text, sessionType));
            }

        } else {
            Fragment webFragment = fragmentManager.findFragmentByTag(WebFragment.TAG);
            if(webFragment==null) {
                webFragment = new WebFragment();
                ((WebFragment)webFragment).setContext(this);
            }
            ((WebFragment)webFragment).searchOrGoToUrl(text, sessionType);
        }
	}

	public void searchOrGoToUrl(String text) {
		searchOrGoToUrl(text, SESSIONTYPE.SESSION_BROWSE);
	}

    private void changeFragment(Fragment newFragment, String newTag) {
        changeFragment(newFragment, newTag, false);
    }

	private void changeFragment(Fragment newFragment, String newTag, boolean displayHomeScreen) {
        Log.d(TAG, "change fragment, new tag: " + newTag);
        Log.d(TAG, "new tag: " + newTag + " - current tag: " + DDGControlVar.mDuckDuckGoContainer.currentFragmentTag+" - prev tag: "+DDGControlVar.mDuckDuckGoContainer.prevFragmentTag);
        if(DDGControlVar.mDuckDuckGoContainer.currentFragmentTag.equals(newTag) && !displayHomeScreen) {
            return;
        }

        boolean backState = true;

        if(!isFinishing() && canCommitFragmentSafely) {
            backState = fragmentManager.popBackStackImmediate(newTag, 0);
        }

        if (displayHomeScreen && fragmentManager.getBackStackEntryCount() > 1) {
            List<Fragment> fragments = fragmentManager.getFragments();
            FragmentTransaction removeTransaction = fragmentManager.beginTransaction();
            for (Fragment f : fragments) {
                if (f != null) {
                    removeTransaction.remove(f);
                    fragmentManager.popBackStack();
                }
            }
            if(!isFinishing()) {
                removeTransaction.commit();
                fragmentManager.executePendingTransactions();
            }
        }



        if(!backState && fragmentManager.findFragmentByTag(newTag)==null) {
            final Fragment currentFragment = fragmentManager.findFragmentByTag(DDGControlVar.mDuckDuckGoContainer.currentFragmentTag);

            FragmentTransaction transaction = fragmentManager.beginTransaction();
            Fragment f = fragmentManager.findFragmentByTag(newTag);
            if(newTag.equals(WebFragment.TAG) || newTag.equals(AboutFragment.TAG  )) {
                //transaction.setCustomAnimations(R.anim.slide_in_from_right, R.anim.empty, R.anim.empty, R.anim.slide_out_to_right);
            } else if(newTag.equals(PrefFragment.TAG) || newTag.equals(HelpFeedbackFragment.TAG)) {
                transaction.setCustomAnimations(R.anim.slide_in_from_bottom2, R.anim.empty, R.anim.empty, R.anim.slide_out_to_bottom2);
            //} else if(newTag.equals(SearchFragment.TAG)) {
                //transaction.setCustomAnimations(R.anim.slide_in_from_bottom2, R.anim.empty, R.anim.empty, R.anim.slide_out_to_bottom2);
            } else {
                transaction.setCustomAnimations(R.anim.empty_immediate, R.anim.empty, R.anim.empty_immediate, R.anim.empty_immediate);
            }
            if(true || f==null) {
                transaction.add(fragmentContainer.getId(), newFragment, newTag);
            } else {
                transaction.show(f);
            }
            if(currentFragment!=null && currentFragment.isAdded()) {
                transaction.hide(currentFragment);
            }
            transaction.addToBackStack(newTag);
            if(canCommitFragmentSafely && !isFinishing()) {
                transaction.commit();
                fragmentManager.executePendingTransactions();
            }
        }
	}

    public boolean isFragmentVisible(String tag) {
        return fragmentManager.findFragmentByTag(tag)!=null && fragmentManager.findFragmentByTag(tag).isVisible();
    }

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {//aaa to remove
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == PREFERENCES_RESULT){
			if (resultCode == RESULT_OK) {
                boolean clearWebCache = data.getBooleanExtra("mustClearWebCache", false);
                if(clearWebCache){
					BusProvider.getInstance().post(new WebViewClearCacheEvent());
                }
                boolean startOrbotCheck = data.getBooleanExtra("startOrbotCheck",false);
                if(startOrbotCheck){
                    searchOrGoToUrl(getString(R.string.OrbotCheckSite));
                }
                boolean switchTheme = data.getBooleanExtra("switchTheme", false);
                if(switchTheme){
                    Intent intent = new Intent(getApplicationContext(), DuckDuckGo.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                }
                
                if(DDGControlVar.homeScreenShowing) {
                	displayHomeScreen();
                }
			}
		}
	}
	
	@Override
	public Object onRetainCustomNonConfigurationInstance() {
	       // return page container, holding all non-view data
	       return DDGControlVar.mDuckDuckGoContainer;
	}
    
	@Override
	protected void onSaveInstanceState(Bundle outState)	{
		AppStateManager.saveAppState(outState, DDGControlVar.mDuckDuckGoContainer);
		super.onSaveInstanceState(outState);
        canCommitFragmentSafely = false;
	}
	
	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState){
		super.onRestoreInstanceState(savedInstanceState);
		
		AppStateManager.recoverAppState(savedInstanceState, DDGControlVar.mDuckDuckGoContainer);

        if(fragmentManager.getBackStackEntryCount()>1) {
            String tag = fragmentManager.getBackStackEntryAt(0).getName();
            fragmentManager.beginTransaction().hide(fragmentManager.findFragmentByTag(tag)).commit();
        }

        DDGActionBarManager.getInstance().updateActionBar(DDGControlVar.mDuckDuckGoContainer.currentFragmentTag);
	}


	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		DDGUtils.displayStats.refreshStats(this);
		super.onConfigurationChanged(newConfig);
        DDGActionBarManager.getInstance().dismissMenu();
	}
	
	public boolean onKeyDown(int keyCode, KeyEvent event) {
	    if ( keyCode == KeyEvent.KEYCODE_MENU ) {
	        return true;
	    }
	    return super.onKeyDown(keyCode, event);
	}

	public DDGAutoCompleteTextView getSearchField() {
        return DDGActionBarManager.getInstance().getSearchField();
	}
	
	@Subscribe
	public void onReloadEvent(ReloadEvent event) {
		reloadAction();
	}
	
	@Subscribe
	public void onSendToExternalBrowserEvent(SendToExternalBrowserEvent event) {
		Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(event.url));
		DDGUtils.execIntentIfSafe(this, browserIntent);
	}

    @Subscribe
    public void onSearchExternalEvent(SearchExternalEvent event) {
        DDGUtils.searchExternal(this, event.query);
    }
	
	@Subscribe
	public void onShareSearchEvent(ShareSearchEvent event) {
		Sharer.shareSearch(this, event.query);
	}

	@Subscribe
	public void onShareWebPageEvent(ShareWebPageEvent event) {//web fragment
		Sharer.shareWebPage(this, event.url, event.url);
	}

    @Subscribe
	public void onSuggestionPaste(SuggestionPasteEvent event) {
        getSearchField().pasteQuery(event.query);
	}

	@Subscribe
	public void onDisplayScreenEvent(DisplayScreenEvent event) {
        displayScreen(event.screenToDisplay, event.clean);
	}

	@Subscribe
	public void onRequestOpenWebPageEvent(RequestOpenWebPageEvent event) {
		searchOrGoToUrl(event.url, event.sessionType);
	}

	@Subscribe
	public void onStopActionEvent(StopActionEvent event) {
		stopAction();
	}

    @Subscribe
    public void onConfirmDialogOkEvent(ConfirmDialogOkEvent event) {
        switch(event.action) {
            case DDGConstants.CONFIRM_CLEAR_COOKIES:
                DDGWebView.clearCookies();
                break;
            case DDGConstants.CONFIRM_CLEAR_WEB_CACHE:
                BusProvider.getInstance().post(new WebViewClearCacheEvent());
                break;
            default:
                break;
        }
    }

    @Subscribe
    public void onWebViewClearCacheEvent(WebViewClearCacheEvent event){
        WebStorage.getInstance().deleteAllData();
    }

    @Subscribe
    public void onRemoveWebFragmentEvent(RemoveWebFragmentEvent event) {
        if(!isFinishing() && canCommitFragmentSafely) {
            fragmentManager.popBackStackImmediate();
        }
    }

    @Subscribe
    public void onDisplayHomeScreenEvent(DisplayHomeScreenEvent event) {
        displayHomeScreen();
    }

    @Subscribe
    public void onAutoCompleteResultClickEvent(AutoCompleteResultClickEvent event) {
        SuggestObject suggestObject = DDGControlVar.mDuckDuckGoContainer.acAdapter.getItem(event.position );
        if (suggestObject != null) {
            SuggestType suggestType = suggestObject.getType();
            if(suggestType == SuggestType.TEXT) {
                if(PreferencesManager.getDirectQuery()){
                    String text = suggestObject.getPhrase().trim();
                    if(suggestObject.hasOnlyBangQuery()){
                        getSearchField().addTextWithTrailingSpace(suggestObject.getPhrase());
                    }else{
                        searchOrGoToUrl(text);
                    }
                }
            }
            else if(suggestType == SuggestType.APP) {
                DDGUtils.launchApp(DuckDuckGo.this, suggestObject.getSnippet());
            }
        }
    }

    @Subscribe
    public void onWebViewItemMenuClickEvent(WebViewItemMenuClickEvent event) {
        onOptionsItemSelected(event.item);
    }
}
