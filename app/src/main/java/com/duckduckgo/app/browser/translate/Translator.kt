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
        translationEngine: TranslationEngine,
    )

    fun translate(
        webView: WebView,
    )
}

@ContributesBinding(AppScope::class)
class TranslatorJS @Inject constructor() : Translator {
    override fun addJsInterface(webView: WebView, translationEngine: TranslationEngine) {
        webView.addJavascriptInterface(TranslationJavascriptInterface(translationEngine), TranslationJavascriptInterface.JAVASCRIPT_INTERFACE_NAME)
    }

    override fun translate(webView: WebView) {
        webView.loadUrl(
            """
                javascript:(function() {
                    var cache = 0;
                    var translations = 0;
                    const translationCache = new Map();
                    var translationCanceled = false;
                    
                    function translateTextNodes(node) {
                        if (node.nodeType === Node.TEXT_NODE) {
                            translateBlock(node)
                        } else if (isTranslatableNode(node)) {
                            for (var i = 0; i < node.childNodes.length && translationCanceled === false; ++i) {
                                translateTextNodes(node.childNodes[i]);
                            }
                        }
                    }
                    
                    function isTranslatableNode(node) {
                        if (node.nodeType === Node.ELEMENT_NODE && node.tagName) {
                            const tagName = node.tagName.toLowerCase();
                            return !['script', 'style', 'meta', 'link', 'noscript', 'iframe', 'canvas', 'object', 'embed', 'applet', 'svg', 'audio', 'video', 'map', 'area', 'track', 'base', 'param', 'source', 'input', 'textarea', 'select', 'option'].includes(tagName);
                        }
                        return false;
                    }
                    
                    function translateBlock(node) {
                        const originalText = node.textContent;
                        const trimmedText = originalText.trim();
                        
                        if (trimmedText.length > 0) {
                            if (translationCache.has(trimmedText)) {
                                node.textContent = applyWhitespace(originalText, translationCache.get(trimmedText));
                                cache++;
                                return;
                            }                    
                            
                            const translation = ${TranslationJavascriptInterface.JAVASCRIPT_INTERFACE_NAME}.translate(trimmedText);
                            if (translation.length === 0) {
                                console.log("$$$ Translation error, cancelling...");
                                translationCanceled = true;
                                return;
                            }
                            
                            translations++;
                            translationCache.set(trimmedText, translation);
                            
                            node.textContent = applyWhitespace(originalText, translation);
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
                                    console.log("$$$ Mutation started");
                                    translateTextNodes(node);
                                    console.log("$$$ Mutation finished, cache hits: " + cache + ", translations: " + translations);
                                });
                            });
                        });
            
                        observer.observe(document.body, {
                            childList: true,
                            subtree: true,
                        });
                    }
                    
                    console.log("$$$ Translation started");
                    translateTextNodes(document.body);
                    console.log("$$$ Translation finished, cache hits: " + cache + ", translations: " + translations);
                    cache = 0;
                    translations = 0;
                    ${TranslationJavascriptInterface.JAVASCRIPT_INTERFACE_NAME}.onTranslationFinished();
                    observeDOMChanges();
                })();
            """,
        )
    }
}
