package org.halalz.kahftube.extentions

import android.webkit.WebView

/**
 * Created by Asif Ahmed on 17/1/24.
 */

fun WebView.injectJavascriptFileFromAsset(fileName: String) {
    val jsCode = this.context.readAssetFile(fileName)
    this.evaluateJavascript("javascript:(function() { $jsCode })()", null)
}
