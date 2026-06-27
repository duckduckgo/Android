/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.translations.impl

/**
 * The JavaScript that drives in-place page translation, run against the live DOM via
 * [android.webkit.WebView.evaluateJavascript]. Kept here, isolated from the orchestration, because it is
 * verified verbatim against a jsdom harness — do not reformat the algorithm.
 *
 * Note: this is a one-shot pass over the current DOM. Content inserted *after* translation (lazy-loaded
 * sections, infinite scroll, SPA navigation, AJAX-injected widgets) is not picked up — covering that needs
 * a MutationObserver that re-translates new subtrees, which is a separate follow-up.
 */
internal object TranslationDomJs {

    // Blocks that aren't laid out (display:none, detached) report a zero-size rect; we sort them last so
    // currently-visible content is translated first, but still translate them (a collapsed carousel/menu is
    // already in the target language by the time the user reveals it). Must match HIDDEN_BLOCK_TOP in Kotlin.
    private const val HIDDEN_TOP = 1_000_000_000

    /** Samples the visible page text used for language detection. Returns a JSON-encoded string. */
    const val DETECT_SAMPLE = "(function() { return document.body ? document.body.innerText.slice(0, 5000) : ''; })();"

    // Collects two kinds of translation unit, walking the DOM and skipping script/style/etc.,
    // contentEditable, translate="no", and anything already translated:
    //  (a) BLOCK fragments — the smallest elements whose element-children are ALL inline (so their
    //      innerHTML is a clean text + inline-markup fragment: a paragraph, heading, headline-in-an-
    //      anchor, list item, …). Translated whole as HTML (isHtml=true) and written back via innerHTML.
    //  (b) ORPHAN text nodes — when descending into a mixed container (one with block-level/custom
    //      children), its direct child text nodes with real text would otherwise be missed; collected
    //      and translated as plain text (isHtml=false), written back via nodeValue.
    // Element/node refs are stashed on window.__ddgTranslateBlocks / __ddgTranslateTextNodes for write-back,
    // keyed by index. Each block also reports its absolute document top (or HIDDEN_TOP if not laid out) so the
    // caller can translate viewport-first. Returns
    // { blocks: [innerHTML…], blockTops: [number…], texts: [nodeValue…], viewportTop, viewportHeight }.
    val COLLECT_UNITS = """
        (function() {
            var INLINE = {
                A: 1, SPAN: 1, STRONG: 1, EM: 1, B: 1, I: 1, U: 1, S: 1, SMALL: 1, SUB: 1, SUP: 1,
                MARK: 1, ABBR: 1, CITE: 1, Q: 1, TIME: 1, KBD: 1, VAR: 1, SAMP: 1, BDI: 1, BDO: 1,
                DFN: 1, DATA: 1, INS: 1, DEL: 1, LABEL: 1, BR: 1, WBR: 1, IMG: 1, PICTURE: 1, FONT: 1,
                NOBR: 1, RUBY: 1, RT: 1, RP: 1, OUTPUT: 1, METER: 1, PROGRESS: 1
            };
            var SKIP = {
                SCRIPT: 1, STYLE: 1, NOSCRIPT: 1, SVG: 1, MATH: 1, CODE: 1, PRE: 1, TEXTAREA: 1,
                IFRAME: 1, SELECT: 1
            };
            if (!document.body) { return { blocks: [], blockTops: [], texts: [], viewportTop: 0, viewportHeight: 0 }; }

            var scrollY = window.pageYOffset || window.scrollY || 0;
            var HIDDEN_TOP = $HIDDEN_TOP;

            function allChildrenInline(el) {
                var children = el.children;
                for (var i = 0; i < children.length; i++) {
                    if (!INLINE[children[i].tagName]) { return false; }
                }
                return true;
            }

            function documentTop(el) {
                var rect = el.getBoundingClientRect();
                if (rect.width === 0 && rect.height === 0) { return HIDDEN_TOP; }
                return rect.top + scrollY;
            }

            var blocks = [];
            var blockHtmls = [];
            var blockTops = [];
            var textNodes = [];
            var textValues = [];
            var stack = [document.body];
            while (stack.length > 0) {
                var el = stack.pop();
                if (!el || el.nodeType !== 1) { continue; }
                if (SKIP[el.tagName] || el.isContentEditable || el.hasAttribute('data-tk-done') || el.getAttribute('translate') === 'no') { continue; }
                if (allChildrenInline(el)) {
                    if (el.textContent && el.textContent.trim().length > 0) {
                        blocks.push(el);
                        blockHtmls.push(el.innerHTML);
                        blockTops.push(documentTop(el));
                    }
                    continue; // its children are inline and part of this fragment
                }
                // Mixed container: collect its direct (orphan) text nodes, then descend into elements.
                var childNodes = el.childNodes;
                for (var i = 0; i < childNodes.length; i++) {
                    var n = childNodes[i];
                    if (n.nodeType === 3 && !n.__tkDone && n.nodeValue && n.nodeValue.trim().length > 0) {
                        textNodes.push(n);
                        textValues.push(n.nodeValue);
                    }
                }
                var children = el.children;
                for (var j = 0; j < children.length; j++) {
                    stack.push(children[j]);
                }
            }
            window.__ddgTranslateBlocks = blocks;
            window.__ddgTranslateTextNodes = textNodes;
            return {
                blocks: blockHtmls,
                blockTops: blockTops,
                texts: textValues,
                viewportTop: scrollY,
                viewportHeight: window.innerHeight || 0
            };
        })()
    """

    // Writes a chunk of translated block fragments back in place: blocks[indices[k]].innerHTML = translations[k],
    // marked data-tk-done so a re-run skips them. Indices address the stash from COLLECT_UNITS, so chunks can be
    // applied in any order. The stash is left in place for later chunks; call [cleanup] when done.
    fun applyBlocks(indicesJson: String, translationsJson: String): String =
        """
        (function() {
            var indices = $indicesJson;
            var translations = $translationsJson;
            var blocks = window.__ddgTranslateBlocks;
            if (blocks) {
                for (var i = 0; i < indices.length && i < translations.length; i++) {
                    var el = blocks[indices[i]];
                    if (el) {
                        el.innerHTML = translations[i];
                        el.setAttribute('data-tk-done', '');
                    }
                }
            }
        })();
        """.trimIndent()

    // Writes the translated orphan text nodes back via nodeValue (flagged __tkDone), in collection order.
    fun applyTexts(translationsJson: String): String =
        """
        (function() {
            var translations = $translationsJson;
            var textNodes = window.__ddgTranslateTextNodes;
            if (textNodes) {
                for (var k = 0; k < textNodes.length && k < translations.length; k++) {
                    textNodes[k].nodeValue = translations[k];
                    textNodes[k].__tkDone = true;
                }
            }
        })();
        """.trimIndent()

    /** Drops the write-back stashes once every chunk has been applied. */
    const val CLEANUP = """
        (function() {
            window.__ddgTranslateBlocks = null;
            window.__ddgTranslateTextNodes = null;
        })();
    """
}
