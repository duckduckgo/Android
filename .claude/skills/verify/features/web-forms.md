# Web pages and forms

The user taps links and fills form fields inside a loaded page. This covers anything rendered by the tab's
WebView: navigation by link, focus, typing, and submitting.

## Sub-features

- `web-link` tapping a link navigates the tab to its target.
- `web-input` tapping a field focuses it, shows the keyboard, and typed text lands in the field.
- `web-submit` the submit button sends the form.

## How to get to it (user POV)

- Load `privacy-test-pages.site`, then tap "Address form submission detection and autofill" (or any form page linked from there).
- Direct: type `privacy-test-pages.site/autofill/address-form-submission.html` in the address bar.

## Driving it with verify.sh

Preconditions: baseline plus the DevTools step from SKILL.md; `privacy-test-pages.site` loaded; `$V web targets` lists it as `visible attached`.

- **Link.** `$V web tap 'a[href$="/autofill/address-form-submission.html"]'`, then `$V web eval 'location.href'` returns that URL.
- **Field.** `$V web tap '#city'`, `$V web eval 'document.activeElement.id'` returns `"city"`. `$V type "Valencia"`, then `$V web eval 'document.querySelector("#city").value'` returns `"Valencia"`. `$V shot web-form-typed` shows the focused field and the keyboard.
- **Submit.** `$V key KEYCODE_BACK` to drop the keyboard, `$V web tap 'button[type=submit]'`, then `$V web eval 'location.href'` ends with `?` (the page reloads with an empty query). Check the screenshot for any native prompt the feature under test should show.
- Field ids on this page: `given_name`, `last_name`, `address_1`, `address_2`, `city`, `postal`, `phone`.

## Gotchas

- `web tap` scrolls the element to the centre first; take shots after it, not before.
- Close the keyboard before tapping elements low on the page, or the tap lands on the keyboard.
- `privacy-test-pages.site` loads over http here; assert on the path, not the scheme.
