package com.duckduckgo.mobile.android.listener;


public interface MimeDownloadListener {
	public void onDownloadComplete(String filePath);
	public void onDownloadFailed();
}