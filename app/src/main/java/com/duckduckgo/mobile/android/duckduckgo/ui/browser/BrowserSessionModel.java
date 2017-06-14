package com.duckduckgo.mobile.android.duckduckgo.ui.browser;

/**
 * Created by fgei on 6/12/17.
 */

public class BrowserSessionModel {

    private String currentUrl;
    private String title;
    private boolean hasLoaded;
    private int progress;
    private boolean canGoBack;
    private boolean canGoForward;

    public BrowserSessionModel() {
    }

    public String getCurrentUrl() {
        return currentUrl;
    }

    public void setCurrentUrl(String currentUrl) {
        this.currentUrl = currentUrl;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean hasLoaded() {
        return hasLoaded;
    }

    public void setHasLoaded(boolean hasLoaded) {
        this.hasLoaded = hasLoaded;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public boolean canGoBack() {
        return canGoBack;
    }

    public void setCanGoBack(boolean canGoBack) {
        this.canGoBack = canGoBack;
    }

    public boolean canGoForward() {
        return canGoForward;
    }

    public void setCanGoForward(boolean canGoForward) {
        this.canGoForward = canGoForward;
    }
}
