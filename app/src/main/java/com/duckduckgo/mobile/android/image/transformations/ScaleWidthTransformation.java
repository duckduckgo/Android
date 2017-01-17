package com.duckduckgo.mobile.android.image.transformations;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.view.ViewGroup;

import com.duckduckgo.mobile.android.download.AsyncImageView;
import com.squareup.picasso.Transformation;

/**
 * Custom transformation class that crops an image to make it square.
 */
public class ScaleWidthTransformation implements Transformation {
	
	int targetWidth, targetHeight;
	
	public void setTarget(AsyncImageView target) {
		ViewGroup.LayoutParams params =  target.getLayoutParams();
		targetWidth = params.width;
		targetHeight = params.height;
	}

    public void setTarget(AsyncImageView target, double scaleRatio) {
        ViewGroup.LayoutParams params =  target.getLayoutParams();
        targetWidth = (int) (params.width * scaleRatio);
        targetHeight = (int) (params.height * scaleRatio);
    }

    public void setTarget(int target) {
        targetWidth = target;
        targetHeight = target;
    }
	
  @Override public Bitmap transform(Bitmap source) {
		
		RectF defaultRect = new RectF(0, 0, source.getWidth(), source.getHeight());
		RectF screenRect = new RectF(0, 0, targetWidth, targetHeight);
		
		Matrix defToScreenMatrix = new Matrix();
		defToScreenMatrix.setRectToRect(defaultRect, screenRect, Matrix.ScaleToFit.CENTER);

		Bitmap scaled = Bitmap.createBitmap(targetWidth, targetHeight, Config.ARGB_8888);
		Canvas canvas = new Canvas(scaled);
		canvas.drawBitmap(source, defToScreenMatrix, null);
		
		source.recycle();
		return scaled;
  }

  @Override public String key() {
    return "scaleWidth()";
  }
}
