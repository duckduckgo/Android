# Compose hosting

Where `ComposeView` and `setContent` may appear while Compose adoption is limited to the Phase 2
integration scenarios. Two build-breaking lint rules enforce it, `NoComposeViewUsage` and
`NoSetContentUsage`, both decided by `ComposeHostingPolicy` in `lint-rules`.

Phase 2 tech design: https://app.asana.com/1/137249556945/task/1219547140965597

---

## Allowed without any configuration

- `design-system` and `design-system-internal`
- Any module whose name ends in `-internal`. These ship only in the `internal` flavor.
- Any file under a `src/internal/` source set, in `app` or in a library module.

Write internal tooling, demos and spikes there. Nothing else is needed.

---

## Production code

A Compose host in production code is a reference contribution of one of the Phase 2 scenarios and
needs that scenario's approved tech design first. Once approved, list the hosting file in the
module's `lint.xml` (create it next to the module's `build.gradle` if it does not exist):

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
- The entry is reviewed with the scenario tech design and revisited at its review date, together
  with the follow-up task that retires the View fallback.
