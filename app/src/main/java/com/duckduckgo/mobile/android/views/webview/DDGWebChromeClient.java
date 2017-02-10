package com.duckduckgo.mobile.android.views.webview;

import com.duckduckgo.mobile.android.actionbar.DDGActionBarManager;
import com.duckduckgo.mobile.android.fragment.WebFragment;
import com.duckduckgo.mobile.android.util.DDGControlVar;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.media.MediaPlayer;
import android.opengl.Visibility;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.VideoView;

public class DDGWebChromeClient extends WebChromeClient implements MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

    private Activity activity;
    private View hideContent;
    private ViewGroup showContent;

    private boolean isVideoFullscreen;
    private FrameLayout videoViewContainer;
    private CustomViewCallback videoViewCallback;

    public DDGWebChromeClient(Activity activity, View hideContent, ViewGroup showContent) {
        this.activity = activity;
        this.hideContent = hideContent;
        this.showContent = showContent;
    }
	
	@Override
	public void onProgressChanged(WebView view, int newProgress) {
		super.onProgressChanged(view, newProgress);
		
		if(view.getVisibility() != View.VISIBLE) {
			return;
		}

        if(!DDGControlVar.mCleanSearchBar) {
            DDGActionBarManager.getInstance().setProgress(newProgress);
        }
	}

    @Override
    public void onShowCustomView(View view, int requestedOrientation, CustomViewCallback callback) {
        onShowCustomView(view, callback);
    }

	@Override
	public void onShowCustomView(View view, CustomViewCallback callback) {
        if(view instanceof FrameLayout) {
            FrameLayout layout = (FrameLayout)view;
            View focusedChild = layout.getFocusedChild();

            this.isVideoFullscreen = true;
            this.videoViewContainer = layout;
            this.videoViewCallback = callback;

            hideContent.setVisibility(View.INVISIBLE);
            showContent.addView(videoViewContainer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            showContent.setVisibility(View.VISIBLE);

            if (focusedChild instanceof VideoView) {
                VideoView videoView = (VideoView) focusedChild;
                videoView.setOnCompletionListener(this);
                videoView.setOnErrorListener(this);
            }
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                addFullscreenFlag();
            }
        }
	}

	@Override
	public void onHideCustomView() {
        if(isVideoFullscreen) {
            showContent.setVisibility(View.INVISIBLE);
            showContent.removeView(videoViewContainer);
            hideContent.setVisibility(View.VISIBLE);

            if(videoViewCallback!=null) {
                videoViewCallback.onCustomViewHidden();
            }

            isVideoFullscreen = false;
            videoViewContainer = null;
            videoViewCallback = null;

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                removeFullscreenFlag();
            }
        }
	}


    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void addFullscreenFlag() {
        View decorView = activity.getWindow().getDecorView();
        int flags = 0;

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            flags = getImmersiveStickyFlag();
        } else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            flags = getFullscreenFlags();
        }
        decorView.setSystemUiVisibility(flags);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private int getFullscreenFlags() {
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_FULLSCREEN;
        return flags;
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private int getImmersiveStickyFlag() {
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        return flags;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void removeFullscreenFlag() {
        View decorView = activity.getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    public boolean isVideoFullscreen() {
        return isVideoFullscreen;
    }

    @Override
    public void onCompletion(MediaPlayer mp) // Video finished playing, only called in the case of VideoView (typically API level <11)
    {
        onHideCustomView();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        return false;
    }

}
