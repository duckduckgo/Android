# Web pages and forms

The user taps links and fills form fields inside a loaded page: navigation by link, focus, typing and
submitting, all rendered by the tab's WebView.

## Sub-features

- `web-link` tapping a link navigates the tab to its target.
- `web-input` tapping a field focuses it, shows the keyboard, and typed text lands in the field.
- `web-submit` the submit button sends the form.

## How to get to it (user POV)

- Load `privacy-test-pages.site`, then tap "Address form submission detection and autofill" (or any form page linked from there).
- Direct: the address bar with `privacy-test-pages.site/autofill/address-form-submission.html`.

## Driving it with verify.sh

Preconditions: baseline; Web Content Debugging on; `$V t browse --url https://privacy-test-pages.site/`; `$V web targets` lists it as `visible attached`.

- **Link.** `$V t record start web-form.mp4`, `$V web tap 'a[href$="/autofill/address-form-submission.html"]'`, then `$V web eval 'location.pathname'` returns `"/autofill/address-form-submission.html"`.
- **Field.** `$V web tap '#city'`, `$V web eval 'document.activeElement.id'` returns `"city"`. `$V web type Valencia`, then `$V web eval 'document.querySelector("#city").value'` returns `"Valencia"`. `$V t shot web-form-typed.png` shows the focused field and keyboard.
- **Submit.** `$V t key back` to drop the keyboard, `$V web tap 'button[type=submit]'`, then `$V web eval 'location.search'` is `""` or `"?"` after the reload. Check the screenshot for any native prompt the change under test should show. `$V t record stop`.
- Field ids on this page: `given_name`, `last_name`, `address_1`, `address_2`, `city`, `postal`, `phone`.

## Gotchas

- `web tap` scrolls the element to the centre first; take shots after it, not before.
- Close the keyboard before tapping elements low on the page, or the tap lands on the keyboard.
- Autofill prompts on these pages are native surfaces (`autofill.*` in the catalog); assert them with `$V t assert where`.
- Driven with trapdoor + DevTools: `web-link`, `web-input` (2026-11-13). `web-submit` with the earlier harness.
