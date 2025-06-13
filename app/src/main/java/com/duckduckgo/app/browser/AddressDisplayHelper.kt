package com.duckduckgo.app.browser

import android.content.Context
import androidx.core.net.toUri
import com.duckduckgo.common.utils.baseHost
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

interface AddressDisplayFormatter {
    fun getDisplayAddress(query: String?, url: String?, showsFullUrl: Boolean): String
}

@SingleInstanceIn(ActivityScope::class)
@ContributesBinding(ActivityScope::class)
class RealAddressDisplayFormatter @Inject constructor(
    private val context: Context,
    private val duckPlayer: DuckPlayer,
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
) : AddressDisplayFormatter {
    override fun getDisplayAddress(query: String?, url: String?, showsFullUrl: Boolean): String {
        return when {
            url == null -> ""
            duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(url) -> query ?: url
            showsFullUrl -> url
            duckPlayer.isDuckPlayerUri(url) -> context.getString(R.string.browserDuckPlayerShortUrl)
            else -> url.toUri().baseHost ?: url
        }
    }
}
