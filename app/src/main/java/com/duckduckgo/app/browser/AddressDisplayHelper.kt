package com.duckduckgo.app.browser

import android.content.Context
import androidx.core.net.toUri
import com.duckduckgo.adblocking.api.duckplayer.DuckPlayer
import com.duckduckgo.common.utils.baseHost
import com.duckduckgo.di.scopes.ActivityScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import javax.inject.Inject

interface AddressDisplayFormatter {
    fun getShortUrl(url: String?): String
}

@SingleIn(ActivityScope::class)
@ContributesBinding(ActivityScope::class)
class RealAddressDisplayFormatter @Inject constructor(
    private val context: Context,
    private val duckPlayer: DuckPlayer,
) : AddressDisplayFormatter {
    override fun getShortUrl(url: String?): String {
        return when {
            url == null -> ""
            duckPlayer.isDuckPlayerUri(url) -> context.getString(R.string.browserDuckPlayerShortUrl)
            else -> url.toUri().baseHost ?: url
        }
    }
}
