package com.duckduckgo.mobile.android.actionbar;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.view.menu.MenuBuilder;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Transformation;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.duckduckgo.mobile.android.R;
import com.duckduckgo.mobile.android.activity.KeyboardService;
import com.duckduckgo.mobile.android.bus.BusProvider;
import com.duckduckgo.mobile.android.events.DisplayHomeScreenEvent;
import com.duckduckgo.mobile.android.events.OverflowButtonClickEvent;
import com.duckduckgo.mobile.android.fragment.SearchFragment;
import com.duckduckgo.mobile.android.fragment.WebFragment;
import com.duckduckgo.mobile.android.util.DDGControlVar;
import com.duckduckgo.mobile.android.util.DDGUtils;
import com.duckduckgo.mobile.android.util.SCREEN;
import com.duckduckgo.mobile.android.util.SESSIONTYPE;
import com.duckduckgo.mobile.android.views.DDGOverflowMenu;
import com.duckduckgo.mobile.android.views.autocomplete.DDGAutoCompleteTextView;

public final class DDGActionBarManager implements View.OnClickListener, View.OnLongClickListener {

    public static final String TAG = "ddg_actionbar_manager";

    private Activity activity;
    private Context context;
    private KeyboardService keyboardService;

    private DDGAutoCompleteTextView searchField = null;
    private RelativeLayout searchFieldContainer = null;
    private TextView actionBarTitle;

    private ImageButton homeButton;
    private ImageButton bangButton;
    private ImageButton overflowButton;

    private ProgressBar progressBar;
    private ProgressBarAnimation progressBarAnimation = null;
    private int oldProgress = 0;
    private boolean isProgressVisible = false;

    private DDGOverflowMenu overflowMenu = null;
    private Menu mainMenu;
    private MenuInflater inflater;

    private Toolbar toolbar;

    private String tag;

    private static final DDGActionBarManager DDG_ACTION_BAR_MANAGER = new DDGActionBarManager();

    public static DDGActionBarManager getInstance() {
        return DDG_ACTION_BAR_MANAGER;
    }

    private DDGActionBarManager() {
        // No instances.
    }

    public void init(Activity activity, Context context, Toolbar toolbar) {

        this.activity = activity;
        this.context = context;
        this.toolbar = toolbar;
        this.inflater = activity.getMenuInflater();

        searchFieldContainer = (RelativeLayout) toolbar.findViewById(R.id.search_container);
        actionBarTitle = (TextView) toolbar.findViewById(R.id.actionbar_title);
        Typeface typeface = Typeface.createFromAsset(context.getAssets(), "fonts/Roboto_Medium.ttf");
        actionBarTitle.setTypeface(typeface);

        progressBar = (ProgressBar) toolbar.findViewById(R.id.progress_bar);

        searchField = (DDGAutoCompleteTextView) toolbar.findViewById(R.id.searchEditText);

        homeButton = (ImageButton) toolbar.findViewById(R.id.home);
        homeButton.setOnClickListener(this);
        homeButton.setOnLongClickListener(this);
        bangButton = (ImageButton) toolbar.findViewById(R.id.bang);
        bangButton.setOnClickListener(this);
        bangButton.setOnLongClickListener(this);
        overflowButton = (ImageButton) toolbar.findViewById(R.id.overflow);
        overflowButton.setOnClickListener(this);
        overflowButton.setOnLongClickListener(this);

        keyboardService = new KeyboardService(activity);

        mainMenu = new MenuBuilder(activity);
        inflater.inflate(R.menu.main, mainMenu);
    }

    public DDGAutoCompleteTextView getSearchField() {
        return this.searchField;
    }

    public Toolbar getToolbar() {
        return this.toolbar;
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()) {
            case R.id.home:
                stopProgress();
                setProgressBarVisible(false);
                BusProvider.getInstance().post(new DisplayHomeScreenEvent());
                break;
            case R.id.bang:
                stopProgress();
                setProgressBarVisible(false);
                getSearchField().addBang();
                keyboardService.showKeyboard(getSearchField());
                break;
            case R.id.overflow:
                if(tag.equals(WebFragment.TAG)) {
                    BusProvider.getInstance().post(new OverflowButtonClickEvent(toolbar));
                } else {
                    showMenu();
                }
            default:
                break;
        }

    }

    @Override
    public boolean onLongClick(View v) {
        switch(v.getId()) {
            case R.id.home:
                Toast.makeText(context, "Home", Toast.LENGTH_SHORT).show();
                return true;
            case R.id.bang:
                Toast.makeText(context, "Bang", Toast.LENGTH_SHORT).show();
                return true;
            default:
                return false;
        }
    }

    public void updateActionBar(String tag) {
        Log.d(TAG, "update actionbar: "+tag);

        this.tag = tag;
        SCREEN screen = DDGUtils.getScreenByTag(tag);

        int standardMargin = (int) context.getResources().getDimension(R.dimen.actionbar_margin);
        int overflowVisibleRightMargin = 0;
        int actionButtonVisibleLeftMargin = (int) context.getResources().getDimension(R.dimen.actionbar_searchbar_left_margin_with_button);
        overflowVisibleRightMargin = actionButtonVisibleLeftMargin;
        int leftMargin , rightMargin;

        for(int i=0; i<mainMenu.size(); i++) {
            mainMenu.getItem(i).setEnabled(true);
        }

        switch(screen) {
            case SCR_WEBVIEW:
                showSearchField();

                setActionBarMargins(actionButtonVisibleLeftMargin, standardMargin, overflowVisibleRightMargin, standardMargin);

                setOverflowButton(true);
                setOverflowButtonMarginTop(false);
                setBangButton();
                setHomeButtonMarginTop(false);

                setProgressBarVisible(true);
                toggleProgressBarVisibility(false, false);
                break;
            case SCR_ABOUT:
                showTitle(context.getResources().getString(R.string.about));
                setOverflowButton(false);
                setOverflowButtonMarginTop(false);
                setHomeButton(true);
                setHomeButtonMarginTop(false);
                setProgressBarVisible(false);
                break;
            case SCR_HELP:
                showTitle(context.getResources().getString(R.string.help_feedback));
                setOverflowButton(false);
                setOverflowButtonMarginTop(false);
                setHomeButton(true);
                setHomeButtonMarginTop(false);
                setProgressBarVisible(false);
                break;
            case SCR_SETTINGS:
                showTitle(context.getResources().getString(R.string.settings));
                setOverflowButton(false);
                setOverflowButtonMarginTop(false);
                setHomeButton(true);
                setHomeButtonMarginTop(false);
                setProgressBarVisible(false);
                break;
            default:
                break;
        }
    }

    public void clearSearchBar() {
        searchField.setText("");
        searchField.setCompoundDrawables(null, null, null, null);
    }


    public void toggleProgressBarVisibility(boolean visible, boolean withAnimation) {
        View progressBarContainer = toolbar.findViewById(R.id.progress_container);
        if((!visible && progressBarContainer.getVisibility()==View.GONE) || (visible && progressBarContainer.getVisibility()==View.VISIBLE)) {
            return;
        }

        if(withAnimation) {
            int resId = visible ? R.anim.show_progressbar : R.anim.hide_progressbar;
            Animation animation = AnimationUtils.loadAnimation(context, resId);
            progressBarContainer.setAnimation(animation);
        }
        progressBarContainer.setVisibility(visible ? View.VISIBLE : View.GONE);

    }

    public void setProgressBarVisible(boolean visible) {
        isProgressVisible = visible;

        if(!visible) {
            toggleProgressBarVisibility(false, false);
        } else {
            toggleProgressBarVisibility(true, true);
        }
    }

    public void setProgress(int newProgress) {
        if(!isProgressVisible) {
            return;
        }
        if(newProgress<oldProgress) {
            return;
        }

        toggleProgressBarVisibility(true, true);

        progressBarAnimation = new ProgressBarAnimation(progressBar, oldProgress, newProgress);
        progressBarAnimation.setDuration(500);
        progressBar.startAnimation(progressBarAnimation);
        oldProgress = newProgress;

        if(oldProgress>=100) {
            oldProgress = 0;
            toggleProgressBarVisibility(false, true);
        }
    }

    public void stopProgress() {
        progressBar.clearAnimation();
        progressBar.setProgress(0);
        oldProgress = 0;
    }

    private void showSearchField() {
        toggleActionBarView(true);
    }

    private void showTitle(String title) {
        toggleActionBarView(false);
        setTitle(title);
    }

    private void setTitle(String title) {
        actionBarTitle.setText(title);
    }

    private void toggleActionBarView(boolean searchVisible) {
        searchFieldContainer.setVisibility(searchVisible ? View.VISIBLE : View.GONE);
        actionBarTitle.setVisibility(searchVisible ? View.GONE : View.VISIBLE);
    }

    private void setActionBarMargins(int newLeft, int newTop, int newRight, int newBottom) {
        final RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) searchFieldContainer.getLayoutParams();
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.HONEYCOMB) {
            ValueAnimator leftValueAnimator = ValueAnimator.ofInt(params.leftMargin, newLeft);
            leftValueAnimator.setDuration(250);
            leftValueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    params.leftMargin = (Integer) animation.getAnimatedValue();
                    searchFieldContainer.setLayoutParams(params);
                }
            });
            ValueAnimator rightValueAnimator = ValueAnimator.ofInt(params.rightMargin, newRight);
            rightValueAnimator.setDuration(250);
            rightValueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    params.rightMargin = (Integer) animation.getAnimatedValue();
                    searchFieldContainer.setLayoutParams(params);
                }
            });
            ValueAnimator topValueAnimator = ValueAnimator.ofInt(params.topMargin, newTop);
            topValueAnimator.setDuration(250);
            topValueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    params.topMargin = (Integer) animation.getAnimatedValue();
                    searchFieldContainer.setLayoutParams(params);
                }
            });
            ValueAnimator bottomValueAnimator = ValueAnimator.ofInt(params.bottomMargin, newBottom);
            bottomValueAnimator.setDuration(250);
            bottomValueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    params.bottomMargin = (Integer) animation.getAnimatedValue();
                    searchFieldContainer.setLayoutParams(params);
                }
            });

            leftValueAnimator.start();
            rightValueAnimator.start();
            topValueAnimator.start();
            bottomValueAnimator.start();
        } else {
            params.setMargins(newLeft, newTop, newRight, newBottom);
        }
    }

    private void setHomeButton(boolean visible) {

        Animation fadeIn = AnimationUtils.loadAnimation(context, R.anim.actionbar_button_fade_in);
        Animation fadeOut = AnimationUtils.loadAnimation(context, R.anim.actionbar_button_fade_out);

        if (bangButton.getVisibility() == View.VISIBLE) {
            bangButton.setAnimation(fadeOut);
            bangButton.setVisibility(View.GONE);
        }

        if(visible) {
            if (homeButton.getVisibility() == View.GONE) {
                homeButton.setAnimation(fadeIn);
                homeButton.setVisibility(View.VISIBLE);
            }
        } else if(homeButton.getVisibility() == View.VISIBLE) {
            homeButton.setAnimation(fadeOut);
            homeButton.setVisibility(View.GONE);
        }
    }

    private void setHomeButtonMarginTop(boolean visible) {
        final RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) toolbar.findViewById(R.id.home).getLayoutParams();
        int padding = visible ? (int) context.getResources().getDimension(R.dimen.actionbar_margin) : 0;
        params.topMargin = padding;
    }

    private void setBangButton() {

        Animation fadeIn = AnimationUtils.loadAnimation(context, R.anim.actionbar_button_fade_in);
        Animation fadeOut = AnimationUtils.loadAnimation(context, R.anim.actionbar_button_fade_out);

        if(homeButton.getVisibility()==View.VISIBLE) {
            homeButton.setAnimation(fadeOut);
            homeButton.setVisibility(View.GONE);
        }

        if(bangButton.getVisibility()==View.GONE) {
            bangButton.setAnimation(fadeIn);
            bangButton.setVisibility(View.VISIBLE);
        }
    }

    public void setOverflowButton(boolean visible) {
        Animation fadeIn = AnimationUtils.loadAnimation(context, R.anim.actionbar_button_fade_in);
        Animation fadeOut = AnimationUtils.loadAnimation(context, R.anim.actionbar_button_fade_out);
        if(visible) {
            if(overflowButton.getVisibility()==View.GONE) {
                overflowButton.setAnimation(fadeIn);
                overflowButton.setVisibility(View.VISIBLE);
            }
        } else {
            if(overflowButton.getVisibility()==View.VISIBLE) {
                overflowButton.setAnimation(fadeOut);
                overflowButton.setVisibility(View.GONE);
            }
        }
    }

    private void setOverflowButtonMarginTop(boolean visible) {
        final RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) toolbar.findViewById(R.id.overflow).getLayoutParams();
        int padding = visible ? (int) context.getResources().getDimension(R.dimen.actionbar_margin) : 0;
        params.topMargin = padding;
    }

    public void setSearchBarText(String text) {
        if(text==null) {
            text = "";
        }

        DDGControlVar.mDuckDuckGoContainer.currentUrl = text;

        searchField.setFocusable(false);
        searchField.setFocusableInTouchMode(false);
        searchField.setText(DDGUtils.getUrlToDisplay(text));
        searchField.setFocusable(true);
        searchField.setFocusableInTouchMode(true);

    }

    public void resetScreenState() {
        clearSearchBar();
        DDGControlVar.mDuckDuckGoContainer.sessionType = SESSIONTYPE.SESSION_BROWSE;
    }

    public void showMenu() {
        overflowMenu = new DDGOverflowMenu(activity);
        overflowMenu.setMenu(mainMenu);
        overflowMenu.show(overflowButton);
    }

    public void dismissMenu() {
        if(overflowMenu!=null && overflowMenu.isShowing()) {
            overflowMenu.dismiss();
        }
    }

    public class ProgressBarAnimation extends Animation {
        private ProgressBar progressBar;
        private float from;
        private float  to;

        public ProgressBarAnimation(ProgressBar progressBar, float from, float to) {
            super();
            this.progressBar = progressBar;
            this.from = from;
            this.to = to;
            progressBar.setProgress(0);
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            super.applyTransformation(interpolatedTime, t);
            float value = from + (to - from) * interpolatedTime;
            progressBar.setProgress((int) value);
        }

    }

}
