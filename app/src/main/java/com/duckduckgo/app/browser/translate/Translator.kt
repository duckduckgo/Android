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
                    if (node.nodeType === Node.TEXT_NODE) {
                        console.log("$$$ Node text: " + node.textContent);
                        asyncTranslateBlock(node);
                    } else if (isTranslatableNode(node)) {
                        node.childNodes.forEach(translateTextNodes);
                    }
                }
            
                function isTranslatableNode(node) {
                    if (node.nodeType === Node.ELEMENT_NODE) {
                        const tagName = node.tagName.toLowerCase();
                        return !['script', 'style', 'meta', 'link', 'noscript', 'iframe', 'canvas', 'object', 'embed', 'applet', 'svg', 'audio', 'video', 'map', 'area', 'track', 'base', 'param', 'source', 'input', 'textarea', 'select', 'option'].includes(tagName);
                    }
                    console.log("$$$ Node tag: " + node.tagName.toLowerCase());
                    return false;
                }
                
                const translationCache = new Map();
                
                async function asyncTranslateBlock(node) {
                    const originalText = node.textContent;
                    const trimmedText = originalText.trim();
                
                    if (trimmedText.length > 0) {
                        // Check cache first
                        if (translationCache.has(trimmedText)) {
                            node.textContent = applyWhitespace(originalText, translationCache.get(trimmedText));
                            return;
                        }
                
                        try {
                            console.log("$$$ Translating " + trimmedText);
                            const translation = ${TranslatorJavascriptInterface.JAVASCRIPT_INTERFACE_NAME}.translate(trimmedText);
                            translationCache.set(trimmedText, translation);
                            node.textContent = applyWhitespace(originalText, translation);
                        } catch (error) {
                            console.error("Translation error:", error);
                        }
                    }
                }
                
                function applyWhitespace(originalText, translatedText) {
                    const leadingWhitespace = originalText.match(/^\s*/)[0];
                    const trailingWhitespace = originalText.match(/\s*$/)[0];
                    return leadingWhitespace + translatedText + trailingWhitespace;
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
