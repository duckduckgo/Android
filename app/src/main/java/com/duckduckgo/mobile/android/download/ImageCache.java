package com.duckduckgo.mobile.android.download;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;

import android.graphics.Bitmap;
import android.os.Handler;

//TODO: Do we want to add any file caching for any objects?

public class ImageCache {
	private static final String TAG = "ImageCache";
	
	private static final int CACHE_CAPACITY = 6; //How many items we can have that the GC won't touch (we still purge it though)
	private static final int PURGE_DELAY = 60000; //milliseconds before we purge all items (60 seconds)

	private FileCache fileCache = null;
		
	public ImageCache(FileCache fileCache) {
		this.fileCache = fileCache;
	}
	
	//The hard cache will hold references that we don't want to lose (in our case the 50 most recent)
	@SuppressWarnings("serial")
	private final HashMap<String, Bitmap> hardBitmapCache = new LinkedHashMap<String, Bitmap>(CACHE_CAPACITY / 2, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(LinkedHashMap.Entry<String, Bitmap> oldest) {
			//If we have too many objects, remove the oldest one and place it in soft reference
			//The garbage collector will periodically remove soft references...
			if (size() > CACHE_CAPACITY) {
				softBitmapCache.put(oldest.getKey(), new SoftReference<Bitmap>(oldest.getValue()));
				return true;
			} else {
				return false;
			}
		}
	};
	
	//The soft cache will hold older references that we don't care as much if the GC cleans up
	private final static ConcurrentHashMap<String, SoftReference<Bitmap>> softBitmapCache = new ConcurrentHashMap<String, SoftReference<Bitmap>>(CACHE_CAPACITY / 2);
	
	private final Handler purgeHandler = new Handler();
	
	private final Runnable doPurge = new Runnable() {
		public void run() {
			clearCache();
		}
	};
	
	private String getCleanFileName(String url){
		String preUrl = url.substring(url.lastIndexOf("/")+1);
		int idxQues = preUrl.lastIndexOf("?");
		if(idxQues != -1){
			preUrl = preUrl.substring(0,preUrl.lastIndexOf("?"));
		}
		return preUrl;
	}
	
	public void addBitmapToCache(String url, Bitmap bitmap) {
		if (bitmap != null) {
			synchronized(hardBitmapCache) {
				hardBitmapCache.put(url, bitmap);
			}
			
			//Save the bitmap to the file cache as well
			if (fileCache != null) {
				fileCache.saveBitmapAsFile(getCleanFileName(url), bitmap);
			}			
		}
	}
	
	public Bitmap getBitmapFromCache(String url, boolean onlyUseMemCache) {
		resetPurgeTimer();
		if (url == null) return null;
		
		synchronized(hardBitmapCache) {
			// get() is enough for access-order
			final Bitmap bitmap = hardBitmapCache.get(url);
			if (bitmap != null) {
				return bitmap;
			}
		}
		
		SoftReference<Bitmap> bitmapReference = softBitmapCache.get(url);
		if (bitmapReference != null) {
			final Bitmap bitmap = bitmapReference.get();
			if (bitmap != null) {
				return bitmap;
			} else {
				//Remove the url key since the reference no longer exists
				softBitmapCache.remove(url);
			}
		}
		
		if(onlyUseMemCache){
			return null;
		}
		
		if (fileCache != null) {
			Bitmap bitmap = fileCache.getBitmapFromImageFile(getCleanFileName(url));
			if (bitmap != null) {
				synchronized(hardBitmapCache) {
					hardBitmapCache.put(url, bitmap);
				}
			}
			return bitmap;
		}
		
		return null;		
	}
	
	private void resetPurgeTimer() {
		purgeHandler.removeCallbacks(doPurge);
		purgeHandler.postDelayed(doPurge, PURGE_DELAY);
	}
	
	private void clearCache() {
		hardBitmapCache.clear();
		softBitmapCache.clear();
	}
	
	/**
	 * Purge in-memory cache resources
	 */
	public void purge() {
		doPurge.run();
	}
	
	public void setFileCache(FileCache fileCache) {
		this.fileCache = fileCache;
	}
}
