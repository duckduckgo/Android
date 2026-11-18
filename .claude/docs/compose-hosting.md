# Compose hosting

Where `ComposeView` and `setContent` may appear while Compose adoption is limited to Phase 2, which
covers Scenario 3 only: a new surface (screen, bottom sheet or dialog) built fully in Compose and
hosted in a standard `DuckDuckGoActivity` or `DuckDuckGoFragment`. Two build-breaking lint rules
enforce it, `NoComposeViewUsage` and `NoSetContentUsage`, both decided by `ComposeHostingPolicy` in
`lint-rules`.

Phase 2 tech design: https://app.asana.com/1/137249556945/task/1219547140965597

---

## Allowed without any configuration

- `design-system` and `design-system-internal`
- Any module whose name ends in `-internal`. These ship only in the `internal` flavor.
- Any file under a `src/internal/` source set, in `app` or in a library module.

Write internal tooling, demos and spikes there. Nothing else is needed.

---

## Production code

A Compose host in production code is the Scenario 3 reference contribution and needs the approved
Scenario 3 tech design first. Compose in an existing screen, a Compose-backed plugin contribution or
the migration of an existing screen cannot be approved here; those scenarios are deferred to the
Design System AOR. Once approved, list the hosting file in the module's `lint.xml` (create it next
to the module's `build.gradle` if it does not exist):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<lint>
    <issue id="NoComposeViewUsage">
        <!-- Owner: <team or person>. Task: <Asana URL>. Review by: <YYYY-MM-DD>. -->
        <ignore path="src/main/java/com/duckduckgo/feature/ui/FeatureFragment.kt" />
    </issue>
</lint>
```

Use `NoSetContentUsage` for `setContent` in an Activity. Rules for the exception:

- One `<ignore path>` per file, carrying the owner, the tracking task and the review date in a
  comment. Never ignore a directory and never lower the severity.
- No `@Suppress` annotation or `tools:ignore` attribute in the code itself. The `lint.xml` entry is the
  only accepted form, so every production host is visible in one `git grep`.
- The entry is reviewed with the Scenario 3 tech design and revisited at its review date.

This `lint.xml` list is the Phase 2 mechanism. Phase 2 ends with Compose as the default for new
surfaces, so these rules and this list will be revisited once the reference surface is live.
