package com.duckduckgo.mobile.android.image.transformations;

import android.graphics.Bitmap;

import com.duckduckgo.mobile.android.util.DDGUtils;
import com.squareup.picasso.Transformation;

/**
 * Custom transformation class that makes an image with round corners.
 */
public class RoundCornersTransformation implements Transformation {
	
	float radius;
	
	public void setRadius(float radius) {
		this.radius = radius;
	}
	
  @Override
  public Bitmap transform(Bitmap source) {

	Bitmap rounded = DDGUtils.roundCorners(source, radius);
	if(rounded != null) {
		source.recycle();
		return rounded;
	}
	return source;
  }

  @Override
  public String key() {
	  return "roundCorners()";
  }
}
