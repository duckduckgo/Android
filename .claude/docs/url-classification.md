# Deciding whether a string is a URL or a search query

Read this when handling omnibar input, or anywhere a typed string has to be routed to navigation or
to search.

Use `QueryUrlPredictor` (from `browser-api`). Do **not** use `UriString.isWebUrl()` for the decision —
its regex is too permissive, e.g. `bbc.comcomcomcom` passes.

```kotlin
private fun isNavigate(query: String): Boolean =
    if (queryUrlPredictor.isReady()) {
        queryUrlPredictor.classify(query) is Decision.Navigate
    } else {
        UriString.isWebUrl(query)  // fallback while native lib initialises
    }
```

- `Decision` is a sealed interface with `Navigate(url)` and `Search(query)` — both data classes.
- `isReady()` is false briefly at startup while the native library loads; always guard with a
  `UriString.isWebUrl` fallback.
- `QueryUrlPredictor` lives in `browser-api` and is injectable at `AppScope`. `Decision` is exported
  transitively via `browser-api`'s `api` dependency on `url-predictor-android`.
