/*
 * Copyright (c) 2021 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.browser.logindetection

import android.net.Uri
import com.duckduckgo.app.global.ValidUrl
import com.duckduckgo.app.global.getValidUrl
import org.junit.Assert.*
import org.junit.Test
import java.lang.IllegalArgumentException

class UriAuthExtensionKtTest {

    @Test
    fun whenAuthUrlThenReturnTrue() {
        assertTrue(getValidUrl("https://accounts.google.com/o/oauth2/v2/auth?client_id=283002&scope=openid").isOAuthUrl())
        assertTrue(getValidUrl("https://appleid.apple.com/auth/authorize?client_id=com.spotify.accounts").isOAuthUrl())
        assertTrue(getValidUrl("https://www.amazon.com/ap/oa?client_id=amzn1.application-oa2-client&scope=profile").isOAuthUrl())
        assertTrue(getValidUrl("https://auth.atlassian.com/authorize").isOAuthUrl())
        assertTrue(getValidUrl("https://www.facebook.com/dialog/oauth?display=touch&response_type=code").isOAuthUrl())
        assertTrue(getValidUrl("https://www.facebook.com/v2.0/dialog/oauth?display=touch&response_type=code").isOAuthUrl())
        assertTrue(getValidUrl("https://login.microsoftonline.com/common/oauth2/v2.0/authorize").isOAuthUrl())
        assertTrue(getValidUrl("https://www.linkedin.com/oauth/v2/authorization").isOAuthUrl())
        assertTrue(getValidUrl("https://github.com/login/oauth/authorize").isOAuthUrl())
        assertTrue(getValidUrl("https://api.twitter.com/oauth/authorize?oauth_token").isOAuthUrl())
        assertTrue(getValidUrl("https://api.duosecurity.com/oauth/v1/authorize?response_type=code&client_id").isOAuthUrl())
    }

    @Test
    fun when2FAUrlThenReturnTrue() {
        assertTrue(getValidUrl("https://accounts.google.com/signin/v2/challenge/az?client_id").is2FAUrl())
        assertTrue(getValidUrl(" https://sso.duckduckgo.com/module.php/duosecurity/getduo.php").is2FAUrl())
        assertTrue(getValidUrl(" https://www.amazon.com/ap/cvf/approval").is2FAUrl())
    }

    @Test
    fun whenSSOUrlThenReturnTrue() {
        assertTrue(getValidUrl("https://sso.host.com/saml2/idp/SSOService.php").isSSOUrl())
    }

    fun getValidUrl(url: String): ValidUrl {
        return Uri.parse(url).getValidUrl() ?: throw IllegalArgumentException("")
    }
}
