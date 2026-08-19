# Translations

The app ships in 24 languages. Translation is automated: a Smartling repository connector reads
English strings from branches, and commits the translated files back to the same branch.

`smartling-config.json` (repo root) is the source of truth for the locale list and the file mapping:
every `**/values/strings*.xml` is sent for translation and comes back as
`values-<locale>/strings*.xml`.

## Where strings go

| File | Translated? |
|---|---|
| `<module>/src/main/res/values/strings-<module>.xml` | yes — picked up by the connector |
| `<module>/src/main/res/values/donottranslate.xml` | no — excluded |

Translatable string files must be named `strings-<module>.xml` (`app` uses `strings.xml`), and must
carry both Smartling directives above the `<resources>` element — the `MissingSmartlingRequiredDirectives`
lint rule fails the build otherwise:

```xml
<!-- smartling.entity_escaping = false -->
<!-- smartling.instruction_attributes = instruction -->
```

**During development, add new strings to `donottranslate.xml`.** Move them into the real strings file
only when the copy is final and you're ready to request translations: pushing half-finished copy costs
days, since each round trip through Smartling takes a few.

## Rules for translatable strings

1. **Add an `instruction` only where the string isn't self-evident** — always for a string with a
   placeholder, and for copy whose meaning depends on the screen around it. Translators see the
   `instruction` attribute and nothing else, not the surrounding code and not the screen. Plain
   unambiguous copy ("Settings", "Done") needs none; don't add one for its own sake.

   ```xml
   <string name="authenticationDialogMessage" instruction="Placeholder is the name of a website">%1$s requires a username and password.</string>
   ```

2. **Reuse copy that is already translated** instead of adding a key for it. Common words like "Cancel"
   or "Done" live in the design system's `strings-common-ui.xml` and are translated in all 24 locales,
   so point at the existing string. A duplicate key means paying for a translation we already have.

3. **Use positional placeholders** — `%1$s`, `%2$s`, never bare `%s`. Word order differs by language, so
   translators need to be able to reorder them.

4. **Don't skip plurals.** Use `<plurals>` where the English has a count; quantity sets differ per language.

5. **Entity escaping is off globally.** A string that needs escaping has to flip the directive around
   itself, and flip it back afterwards:

   ```xml
   <!-- smartling.entity_escaping = true -->
   <string name="example">My escaped string here</string>
   <!-- smartling.entity_escaping = false -->
   ```

6. **Constrained UI takes a character limit**, declared above the string:

   ```xml
   <!-- smartling.character_limit = 42 -->
   ```

7. **Never put lint annotations inside a translated string** — the next translation job overwrites the
   file and deletes them. Fix the underlying issue, or record the check in the lint baseline instead.

Prefer whole sentences with placeholders over sentence fragments concatenated in code: languages
reorder and inflect, and a fragment gives the translator nothing to work with.

## Changing strings that are already translated

| Goal | What to do |
|---|---|
| Add a string | Add the English string to the strings file |
| Change English copy | Delete the old key, add a **new key** with the new copy, update references. Leave the other languages alone — Smartling prunes them |
| Remove a string | Delete the English string only. Smartling removes the translations |
| Fix a bad translation | A developer edits it in the Smartling dashboard (DuckDuckGo Android project → language → search the key → Edit Translation). It lands with the next job; to ship sooner, also edit the `values-<locale>` file directly |

Reusing a key with different copy leaves every locale holding a stale translation of the old text,
which is why an English change is delete-plus-new-key rather than an edit in place.

Fixing a translation is the one job that happens outside the repo — it needs a Smartling login, so an
agent cannot do it and should hand it to the developer.

## Reference

This file is the working reference — enough to add a string or request a translation without opening
anything else. The Asana sources are the authority behind it; read one only when this file is silent or
looks wrong, not up front:

- [Android Smartling Translation Guide](https://app.asana.com/1/137249556945/project/1202561462274611/task/1203224618541800) — the connector, branch naming, the Smartling rules
- [Typical Development Flow and FAQs](https://app.asana.com/1/137249556945/project/1202561462274611/task/1211223880022672) — an add/change/remove case this file doesn't cover
- [Tips for better translation tokens](https://app.asana.com/1/137249556945/project/904401899170/task/1200205345312078) — writing copy translators can work with
- [How to Use Smartling for Translations](https://app.asana.com/1/137249556945/project/904401899170/task/1185688234072009) — Smartling account setup, for a developer who needs dashboard access

When translations stall or a translation looks wrong, ask the
[Translation/Localization AOR](https://app.asana.com/1/137249556945/project/904401899170) — people to
talk to, not a document to read.
