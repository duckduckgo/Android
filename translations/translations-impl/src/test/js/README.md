# TranslationDomJs tests

Behavioural tests for the page-translation JavaScript in
`../../main/kotlin/com/duckduckgo/translations/impl/TranslationDomJs.kt`.

That JS only runs inside a `WebView` against a live DOM, so it can't be covered by the JVM
`testDebugUnitTest` suite — there is no DOM on the JVM. These tests run it under [jsdom] instead, and
extract each JS constant/function **verbatim** from the Kotlin source, so the test can't drift from
what actually ships.

## Run

```bash
cd translations/translations-impl/src/test/js
npm ci        # installs the exact jsdom version pinned in package-lock.json
npm test
```

Exit code is non-zero if any assertion fails. `package-lock.json` is committed so runs are
reproducible; the Gradle task (`:translations-impl:testTranslationDomJs`, wired into `jvm_tests` /
`jvm_checks`) does the same `npm ci` automatically when Node is on `PATH`.

## What is and isn't covered

Covered: block vs. orphan-text collection, the skip rules (`script`/`style`/`pre`/`code`,
`translate="no"`, `data-tk-done`), in-place write-back (`applyBlocks`/`applyTexts`) and its
idempotency, and the `MutationObserver` (generation bumps on new content, ignores our own
write-backs, `-1` after disconnect).

Not covered here: geometry. jsdom does no layout, so `getBoundingClientRect()` is always zero and
every block is reported at `HIDDEN_TOP`. The viewport-first ordering that consumes those tops lives
in Kotlin (`RealTranslator.viewportFirstOrder`) and is tested from the JVM suite.

[jsdom]: https://github.com/jsdom/jsdom
