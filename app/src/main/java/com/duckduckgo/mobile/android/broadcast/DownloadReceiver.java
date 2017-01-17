package com.duckduckgo.mobile.android.broadcast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import com.duckduckgo.mobile.android.R;
import com.duckduckgo.mobile.android.util.DDGUtils;

@SuppressLint("NewApi")
public class DownloadReceiver extends BroadcastReceiver {
	
	protected final String TAG = "DownloadReceiver";
	
	DownloadManager downloadManager = null;
	
	public DownloadReceiver() {
	}
	
	public DownloadReceiver(DownloadManager downloadManager) {
		this.downloadManager = downloadManager;
	}
	
	public void setDownloadManager(DownloadManager downloadManager) {
		this.downloadManager = downloadManager;
	}
	
	@Override
	public void onReceive(Context context, Intent intent) {
		if(downloadManager == null) {
			downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
		}

		long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
		Uri uri = null;
		String mimeType = null;
		if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.HONEYCOMB) {
			uri = downloadManager.getUriForDownloadedFile(downloadId);
			mimeType = downloadManager.getMimeTypeForDownloadedFile(downloadId);
		}
		else {
			Cursor c = downloadManager.query(new DownloadManager.Query().setFilterById(downloadId));
			if (c.moveToFirst()) {
				int columnIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);

				if (DownloadManager.STATUS_SUCCESSFUL == c.getInt(columnIndex)) {                        
					mimeType = c.getString(c.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE));
					String uriString = c.getString(c
							.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
					uri = Uri.parse(uriString);
				}
				else {
					// download failed somehow, LOG the reason

					int columnReason = c.getColumnIndex(DownloadManager.COLUMN_REASON);
					int reason = c.getInt(columnReason);

					String failedReason = "";

					switch(reason){
					case DownloadManager.ERROR_CANNOT_RESUME:
						failedReason = "ERROR_CANNOT_RESUME";
						break;
					case DownloadManager.ERROR_DEVICE_NOT_FOUND:
						failedReason = "ERROR_DEVICE_NOT_FOUND";
						break;
					case DownloadManager.ERROR_FILE_ALREADY_EXISTS:
						failedReason = "ERROR_FILE_ALREADY_EXISTS";
						break;
					case DownloadManager.ERROR_FILE_ERROR:
						failedReason = "ERROR_FILE_ERROR";
						break;
					case DownloadManager.ERROR_HTTP_DATA_ERROR:
						failedReason = "ERROR_HTTP_DATA_ERROR";
						break;
					case DownloadManager.ERROR_INSUFFICIENT_SPACE:
						failedReason = "ERROR_INSUFFICIENT_SPACE";
						break;
					case DownloadManager.ERROR_TOO_MANY_REDIRECTS:
						failedReason = "ERROR_TOO_MANY_REDIRECTS";
						break;
					case DownloadManager.ERROR_UNHANDLED_HTTP_CODE:
						failedReason = "ERROR_UNHANDLED_HTTP_CODE";
						break;
					case DownloadManager.ERROR_UNKNOWN:
						failedReason = "ERROR_UNKNOWN";
						break;
					}

					Log.v(TAG, "Download fail reason: " + failedReason);                    	
				}
			}
		}

		// if download failed somehow, skip content viewing intent
		if(uri == null || mimeType == null)
			return;

		String downPath = null;
		{

			int idxSlash = mimeType.indexOf('/') + 1;
			String ext = "tmp";
			if(idxSlash != -1) {
				ext = mimeType.substring(idxSlash);
			}

			FileOutputStream downOutput = null;
			downPath = context.getFilesDir().getAbsolutePath() + File.separator + "down." + ext;
			try {
				downOutput = context.openFileOutput("down."+ext, Context.MODE_WORLD_READABLE);
				InputStream in = context.getContentResolver().openInputStream(uri);
				byte buf[] = new byte[1024];
				int readSize = 0;

				// read downloaded file
				while ( (readSize = in.read(buf)) != -1 ) {
					downOutput.write(buf,0,readSize);
				}
				downOutput.close();

			} catch (FileNotFoundException e1) {
				Toast.makeText(context, context.getString(R.string.ErrorDownloadOpenFail), Toast.LENGTH_LONG).show();
				e1.printStackTrace();
			}
			catch (IOException e1) {
				Toast.makeText(context, context.getString(R.string.ErrorDownloadOpenFail), Toast.LENGTH_LONG).show();
				e1.printStackTrace();
			}

		}

		// intent to view content
		Intent viewIntent = new Intent(Intent.ACTION_VIEW);
		if(downPath != null) {
			viewIntent.setDataAndType(Uri.fromFile(new File(downPath)), mimeType);
		}
		else {
			viewIntent.setDataAndType(uri, mimeType);
		}
		viewIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		DDGUtils.execIntentIfSafe(context, viewIntent);
	}
}