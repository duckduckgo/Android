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
    )

    fun translate(
        webView: WebView,
    )
}

@ContributesBinding(AppScope::class)
class TranslatorJS @Inject constructor() : Translator {
    override fun addJsInterface(
        webView: WebView,
    ) {
        webView.addJavascriptInterface(TranslatorJavascriptInterface(), TranslatorJavascriptInterface.JAVASCRIPT_INTERFACE_NAME)
    }

    override fun translate(webView: WebView) {
        webView.loadUrl(
            """
            javascript:(function() {
                function translateTextNodes(node) {
                    const nodeName = node.nodeName;
                    if (node.nodeType === Node.TEXT_NODE && nodeName !== 'script' && nodeName !== 'style' && nodeName !== 'meta' && nodeName !== 'link') {
                        asyncTranslateBlock(node);
                    } else if (isTranslatableNode(node)) {
                        for (var i = 0; i < node.childNodes.length; i++) {
                            translateTextNodes(node.childNodes[i]);
                        }
                    }
                }
                
                function isTranslatableNode(node) {
                    if (node.nodeType === Node.ELEMENT_NODE) {
                        const tagName = node.tagName.toLowerCase();
                        return tagName !== 'script' && tagName !== 'style' && tagName !== 'meta' && tagName !== 'link';
                    }
                    return true;
                }
                
                async function asyncTranslateBlock(node) {
                    var originalText = node.textContent;
                    if (originalText.trim().length > 0) {
                        console.log("$$$ Translating " + originalText);
                        var leadingWhitespace = originalText.match(/^\s*/)[0];
                        var trailingWhitespace = originalText.match(/\s*${'$'}/)[0];
                        var translation = ${TranslatorJavascriptInterface.JAVASCRIPT_INTERFACE_NAME}.translate(originalText.trim());
                        node.textContent = leadingWhitespace + translation + trailingWhitespace;
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
