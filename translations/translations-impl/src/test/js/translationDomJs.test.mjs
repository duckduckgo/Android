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

// Behavioural tests for the page-translation JavaScript in TranslationDomJs.kt. This JS only runs
// inside a WebView against a live DOM, so it cannot be exercised by the JVM unit-test suite (there is
// no DOM on the JVM). We run it under jsdom instead, extracting each JS constant *verbatim* from the
// Kotlin source so the test can never drift from what ships. See README.md to run it.
//
// jsdom limitation: it performs no layout, so getBoundingClientRect() is always zero and every block
// is reported at HIDDEN_TOP. Geometry/ordering therefore lives in (and is tested from) Kotlin
// (RealTranslator.viewportFirstOrder); here we assert structure, skip rules, write-back and the observer.

import { JSDOM } from 'jsdom'
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const KT = fs.readFileSync(
  path.join(HERE, '../../main/kotlin/com/duckduckgo/translations/impl/TranslationDomJs.kt'),
  'utf8',
)

const HIDDEN_TOP = 1_000_000_000

// --- extract the JS constants/functions verbatim from the Kotlin source -------------------------

function tripleQuoted(name) {
  const m = KT.match(new RegExp('(?:const )?val ' + name + ' = """([\\s\\S]*?)"""'))
  if (!m) throw new Error('could not extract ' + name)
  return m[1]
}

const COLLECT_UNITS = tripleQuoted('COLLECT_UNITS').replace(/\$HIDDEN_TOP/g, String(HIDDEN_TOP))
const INSTALL_OBSERVER = tripleQuoted('INSTALL_OBSERVER')
const POLL_GENERATION = tripleQuoted('POLL_GENERATION')
const DISCONNECT_OBSERVER = tripleQuoted('DISCONNECT_OBSERVER')

function applyBlocksJs(indicesJson, translationsJson) {
  const m = KT.match(/fun applyBlocks\([^)]*\): String =\s*"""([\s\S]*?)"""/)
  if (!m) throw new Error('could not extract applyBlocks')
  return m[1].replace(/\$indicesJson/g, indicesJson).replace(/\$translationsJson/g, translationsJson)
}

function applyTextsJs(translationsJson) {
  const m = KT.match(/fun applyTexts\([^)]*\): String =\s*"""([\s\S]*?)"""/)
  if (!m) throw new Error('could not extract applyTexts')
  return m[1].replace(/\$translationsJson/g, translationsJson)
}

// --- tiny test harness --------------------------------------------------------------------------

let pass = 0
let fail = 0
const eq = (name, actual, expected) => {
  const a = JSON.stringify(actual)
  const e = JSON.stringify(expected)
  if (a === e) {
    pass++
    console.log('  ok  -', name)
  } else {
    fail++
    console.log('FAIL  -', name, '\n        expected', e, '\n        actual  ', a)
  }
}
const ok = (name, cond) => eq(name, !!cond, true)

function page(bodyHtml) {
  const dom = new JSDOM(`<!DOCTYPE html><body>${bodyHtml}</body>`, { runScripts: 'outside-only' })
  return {
    run: (js) => dom.window.eval(js),
    collect: () => dom.window.eval(COLLECT_UNITS),
    window: dom.window,
    document: dom.window.document,
    tick: () => new Promise((r) => setTimeout(r, 0)), // flush MutationObserver microtasks
  }
}

// --- COLLECT_UNITS: structure & skip rules ------------------------------------------------------

{
  const r = page('<p>Hello <strong>world</strong></p>').collect()
  eq('inline-only element becomes one block (innerHTML preserved)', r.blocks, ['Hello <strong>world</strong>'])
  eq('no orphan text for a clean block', r.texts, [])
  eq('jsdom reports no layout, so the block top is HIDDEN_TOP', r.blockTops, [HIDDEN_TOP])
}

{
  const r = page('<script>var x = 1</script><style>.a{}</style><pre>raw</pre><code>x</code><p>real</p>').collect()
  eq('script/style/pre/code are skipped, real text is collected', r.blocks, ['real'])
}

{
  const r = page('<p translate="no">do not translate</p><p>translate me</p>').collect()
  eq('translate="no" is skipped', r.blocks, ['translate me'])
}

{
  const r = page('<p data-tk-done>already done</p><p>fresh</p>').collect()
  eq('data-tk-done blocks are skipped on (re)collection', r.blocks, ['fresh'])
}

{
  const r = page('<div>orphan text<p>child block</p></div>').collect()
  eq('orphan text node in a mixed container is collected as text', r.texts, ['orphan text'])
  eq('child block of a mixed container is still collected', r.blocks, ['child block'])
}

// --- applyBlocks / applyTexts: in-place write-back ----------------------------------------------

{
  const p = page('<p>Hello <strong>world</strong></p>')
  p.collect() // stashes window.__ddgTranslateBlocks
  p.run(applyBlocksJs('[0]', '["Hola <strong>mundo</strong>"]'))
  const el = p.document.querySelector('p')
  eq('applyBlocks rewrites the block innerHTML', el.innerHTML, 'Hola <strong>mundo</strong>')
  ok('applyBlocks marks the block data-tk-done', el.hasAttribute('data-tk-done'))
  eq('a re-collect skips the just-translated block', p.collect().blocks, [])
}

{
  const p = page('<div>orphan text<p>child</p></div>')
  p.collect() // stashes window.__ddgTranslateTextNodes
  p.run(applyTextsJs('["texto huérfano"]'))
  eq('applyTexts rewrites the orphan text node value', p.document.querySelector('div').firstChild.nodeValue, 'texto huérfano')
  eq('a re-collect skips the just-translated text node', p.collect().texts, [])
}

// --- INSTALL_OBSERVER / POLL_GENERATION / DISCONNECT_OBSERVER -----------------------------------

{
  const p = page('<div id="root"></div>')
  const poll = () => p.run(POLL_GENERATION)
  p.run(INSTALL_OBSERVER)
  eq('observer installed, generation starts at 0', poll(), 0)

  const root = p.document.getElementById('root')
  root.innerHTML = '<p>Hello world</p>'
  await p.tick()
  eq('adding a fresh text-bearing node bumps the generation', poll(), 1)

  const el = root.querySelector('p')
  el.setAttribute('data-tk-done', '')
  el.innerHTML = 'Hola mundo' // our own write-back
  await p.tick()
  eq('innerHTML write under data-tk-done does NOT bump the generation', poll(), 1)

  const fresh = p.document.createTextNode('externally added')
  root.appendChild(fresh)
  await p.tick()
  eq('a second fresh text node bumps the generation again', poll(), 2)

  root.appendChild(p.document.createTextNode('   \n  '))
  await p.tick()
  eq('whitespace-only text does NOT bump the generation', poll(), 2)

  p.run(INSTALL_OBSERVER)
  eq('re-installing the observer is a no-op', poll(), 2)

  p.run(DISCONNECT_OBSERVER)
  eq('after disconnect, poll returns -1 (caller stops watching)', poll(), -1)
}

console.log(`\n${pass} passed, ${fail} failed`)
process.exit(fail ? 1 : 0)
