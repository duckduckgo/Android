/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.serp.logos.impl

import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface SerpLogoJavascriptInterface {
    val js: String
}

@ContributesBinding(FragmentScope::class)
class RealSerpLogoJavascriptInterface @Inject constructor() : SerpLogoJavascriptInterface {

    /**
     * JavaScript code to extract the SERP logo from the page.
     * It looks for an element with the class 'js-logo-ddg' and retrieves its background image or data attribute
     * and returns a string in the format "type|url", where type is either "easterEgg" or "normal".
     */
    override val js: String = """
        (function() {
            const element = document.querySelector('.js-logo-ddg');
            if (!element) {
                console.log('DDG logo element not found');
                return null;
            }
            if (element.dataset.dynamicLogo) {
                return 'easterEgg|' + element.dataset.dynamicLogo;
            }
            const style = window.getComputedStyle(element);
            const bgImage = style.backgroundImage;
            if (bgImage && bgImage.includes("url(\"")) {
                const url = bgImage.split('url("')[1].split('")')[0];
                return 'normal|' + url;
            }
            return null;
        })();
    """.trimIndent()
}
