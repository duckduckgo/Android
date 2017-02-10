package com.duckduckgo.mobile.android.download;

import java.io.File;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.widget.Toast;

import com.duckduckgo.mobile.android.R;
import com.duckduckgo.mobile.android.activity.DuckDuckGo;
import com.duckduckgo.mobile.android.listener.MimeDownloadListener;
import com.duckduckgo.mobile.android.tasks.MimeDownloadTask;
import com.duckduckgo.mobile.android.util.DDGUtils;

/**
 * This class handles downloading of music, video, etc. Some options like downloading from a secure URL are supported only for some the Android versions.
 *
 * It contains an anonymous class inherited from MimeDownloadListener to implement manual downloads for older Android versions.
 */
public class ContentDownloader {

	private DownloadManager downloadManager;
	private Context context;

	public ContentDownloader(Context context) {
		this.context = context;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
			this.downloadManager = (DownloadManager) context.getSystemService(DuckDuckGo.DOWNLOAD_SERVICE);
		}
	}

	@SuppressLint("NewApi")
	public void downloadContent(final String url, final String mimeType) {
		// use mimeType to figure out an extension for temporary file
		String extension = decideExtension(mimeType);
		String fileName = "down." + extension;

        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            if(url.startsWith("https")) {
                //download scheme not supported yet
                Toast.makeText(context, R.string.ToastDownloadManagerUnavailable, Toast.LENGTH_SHORT).show();
                return;
            }
        }

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {

            int downloadManagerState = context.getPackageManager().getApplicationEnabledSetting("com.android.providers.downloads");
            if(downloadManagerState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    || downloadManagerState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                    || downloadManagerState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED) {
                Toast.makeText(context, R.string.ToastSchemeNotSupported, Toast.LENGTH_SHORT).show();
                return;
            }

			Uri uri = Uri.parse(url);
			DownloadManager.Request request = new DownloadManager.Request(uri);
			// When downloading music and videos they will be listed in the
			// player
			// (Seems to be available since Honeycomb only)
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
				request.allowScanningByMediaScanner();
				// Notify user when download is completed
				// (Seems to be available since Honeycomb only)
				request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
			}
			// Start download
			downloadManager.enqueue(request);
		} else {
			// manual download for devices below GINGERBREAD
			// TODO AsyncTask here
			MimeDownloadListener mimeListener = new MimeDownloadListener() {

				@Override
				public void onDownloadFailed() {
					// TODO Fail gracefully here... inform the user about failed
					// download!
					Toast.makeText(context, R.string.ErrorDownloadFailed, Toast.LENGTH_LONG).show();
				}

				@Override
				public void onDownloadComplete(String filePath) {
					// intent to view content
					Intent viewIntent = new Intent(Intent.ACTION_VIEW);
					File file = new File(filePath);
					viewIntent.setDataAndType(Uri.fromFile(file), mimeType);
					viewIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					DDGUtils.execIntentIfSafe(context, viewIntent);
				}
			};

			MimeDownloadTask mimeTask = new MimeDownloadTask(mimeListener, url, fileName);
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                mimeTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            } else {
                mimeTask.execute();
            }
		}
	}

	/**
	 * Read the extension from the minetype string, e.g. for 'content/html' it retrieves 'html'
	 *
	 * @param mimeType
	 * @return
     */
	private String decideExtension(final String mimeType) {
		int idxSlash = mimeType.indexOf('/') + 1;
		String ext = "tmp";
		if (idxSlash != -1) {
			ext = mimeType.substring(idxSlash);
		}
		return ext;
	}
}
