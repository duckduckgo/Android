/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser.translate

import android.webkit.WebView
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface Translator {
    fun addJsInterface(
        webView: WebView,
        translate: (String) -> String,
    )

    fun translate(
        webView: WebView,
    )
}

@ContributesBinding(AppScope::class)
class TranslatorJS @Inject constructor() : Translator {
    override fun addJsInterface(
        webView: WebView,
        translate: (String) -> String,
    ) {
        webView.addJavascriptInterface(TranslatorJavascriptInterface(translate), TranslatorJavascriptInterface.JAVASCRIPT_INTERFACE_NAME)
    }

    override fun translate(webView: WebView) {
        webView.loadUrl(
            """
            javascript:(function() {
                function translateTextNodes(node) {
                    if (node.nodeType === Node.TEXT_NODE && node.parentNode.tagName.toLowerCase() !== 'script') {
                        var originalText = node.textContent;
                        if (originalText.trim().length > 0) {
                            var leadingWhitespace = originalText.match(/^\s*/)[0];
                            var trailingWhitespace = originalText.match(/\s*${'$'}/)[0];
                            var translatedText = ${TranslatorJavascriptInterface.JAVASCRIPT_INTERFACE_NAME}.translate(originalText.trim());
                            node.textContent = leadingWhitespace + translatedText + trailingWhitespace;
                        }
                    } else {
                        for (var i = 0; i < node.childNodes.length; i++) {
                            translateTextNodes(node.childNodes[i]);
                        }
                    }
                }
                
                function observeDOMChanges() {
                    const observer = new MutationObserver((mutations) => {
                        mutations.forEach((mutation) => {
                            mutation.addedNodes.forEach((node) => {
                                translateTextNodes(node);
                            });
                        });
                    });
                    
                    observer.observe(document.body, {
                        childList: true,
                        subtree: true,
                    });
                }
                
                translateTextNodes(document.body);
                observeDOMChanges();
            })();
        """,
        )
    }
}
