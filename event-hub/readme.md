# Event Hub
Handles web events from Content Scope Scripts (C-S-S) via the eventHub feature, aggregating counters into bucketed pixels and firing immediate pixels based on remote configuration.

## Architecture
- **event-hub-impl**: Feature plugins, config parser, pixel manager, JS message handler, pixel firing, Room database for pixel state

## How it works
1. Remote config defines pixel telemetry under the `eventHub` feature
2. The `webEvents` CSS feature routes `webEvent` messages from CSS to native
3. Each pixel parameter has a `source` that matches incoming event types
4. Duplicate events are dropped (see below). Each remaining event fires its immediate pixels, and updates the parameters of every period pixel watching it
5. On app foreground, the lifecycle observer checks if any pixel periods have elapsed
6. If elapsed, a pixel is fired with bucketed parameter values and an `attributionPeriod` timestamp

## Remote config parsing
- Supported triggers are `period` (an absent `type` means `period`) and `immediate_v2`. Entries with any other trigger type, such as `immediate` or `immediate_v3`, are skipped. Plain `immediate` means firing on every raw occurrence, which Android never implemented; `immediate_v2` fires once per de-duplicated event.
- Supported parameters are `counter` and `data`. A period entry needs at least one valid counter, because only a counter landing in a bucket fires it. An immediate entry may have no parameters.
- Each `telemetry` entry is parsed on its own, so an entry this client can't parse is skipped and logged without affecting the others.

## Immediate pixels and data parameters
- An `immediate_v2` pixel fires once for each delivered event of its trigger `source`, carrying its `data` parameters read from that event. It has no period and no persisted state, isn't gated on the app being in the foreground, and reads the latest config on every event. An event still queued when the hub is switched off fires nothing.
- A `data` parameter carries `data[dataKey]` from the event payload as compact JSON, percent-encoded once and sent as an already-encoded pixel parameter (e.g. `"overlay"` is sent as `%22overlay%22`). It's left out when the payload lacks the key.
- On a period pixel, a `data` parameter takes its value from the latest delivered event of its `source`, and an event without the key clears it. Data parameters ride along when a counter fires the pixel but never fire it on their own.
- Pixels defined in remote config need an entry in `PixelDefinitions/pixels/definitions/event_hub.json5`. `data` parameters forward values from the page, so they need privacy triage, and bounded values are preferred.

## De-duplication
- Each web event is checked once, before any counter sees it. The key is the event `type` plus its `data`, per tab. `data` objects that are equal as JSON values, in any member order, are one key, and an omitted `data` is the same as `{}`.
- A duplicate on the tab's current page reaches no handler: no counter, data parameter, immediate pixel or experiment metric. The first occurrence reaches every one of them, and uses up the key even if no pixel is counting at that moment.
- A tab's keys are cleared when it navigates to a different URL. A same-URL reload, or a single-page-app route change that doesn't register as a navigation, doesn't clear them.
- Events without a tab (empty `webViewId`) are never de-duplicated.
- The decision is made as each event reaches the hub, against the page its tab is on at that moment.
- Known limitation: events reach the hub through an asynchronous JS messaging queue, while navigation comes from the UI thread. An event still queued when its tab navigates can be checked against the next page, and use up that page's key.

## Experiment metrics
- An experiment can declare metrics on Event Hub events in its own `settings.metrics`. Each metric names an `event` and its `conversions` (`windows` × `thresholds`). On each event the handler asks the features that run experiments which are active: `ContentScopeExperiments.getActiveExperiments()` (the same list content scope scripts get on page start) and `BlockListExperiment.activeExperiment()` (the one experiment the blocklist is downloaded, enrolled and measured for). When `contentScopeExperiments` itself is off, no CSS experiment is active, matching what content scope scripts run. The framework skips experiments the user isn't enrolled in.
- Every event that passes de-duplication is handed to `ExperimentMetricsHandler`, which sends one `MetricsPixel` of type `COUNT_WHEN_IN_WINDOW` per threshold. The experiments framework decides enrolment, conversion windows, counting and firing, under its existing `experiment_metrics_<experiment>_<cohort>` pixel.
- Metric definitions are read from the active experiments' settings on each event, so a config change applies to the next one. Each experiment's parsed definitions are reused while its settings are unchanged. Invalid metrics are skipped and logged, and an experiment whose settings can't be read is skipped.
- Metric names used by Android's built-in metrics on the same experiments (`search`, `app_use`, `duck_ai_prompt_sent`, `duck_ai_new_chat`, `privacyToggleUsed`) are rejected, because a config metric with the same name would share the framework's counter. If a new built-in metric is attached to every active experiment, add its name to `ExperimentMetricsConfigParser.RESERVED_NAMES`.
- A failing send is logged and skipped; it doesn't stop the other sends.
- Metrics only flow while both `eventHub` (the hub) and `webEvents` (the producer of web events) are enabled.
