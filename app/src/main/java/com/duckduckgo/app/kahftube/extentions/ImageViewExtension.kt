package org.halalz.kahftube.extentions

import android.util.Log
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions

/**
 * Created by Asif Ahmed on 24/1/24.
 */

fun ImageView.loadFromUrl(url: String?) {
    url?.let {
        Log.d("loadFromUrl", "url: $url")
        Glide.with(this)
            .load(url)
            .transition(DrawableTransitionOptions.withCrossFade(200))
            .into(this)
    }
}
