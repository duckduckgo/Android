# Event Hub
Handles web events from Content Scope Scripts (C-S-S) via the eventHub feature, aggregating counters and firing bucketed pixels based on remote configuration.

## Architecture
- **event-hub-impl**: Feature plugins, config parser, pixel manager, JS message handler, pixel firing, Room database for pixel state

## How it works
1. Remote config defines pixel telemetry under the `eventHub` feature
2. The `webEvents` CSS feature routes `webEvent` messages from CSS to native
3. Each pixel parameter has a `source` that matches incoming event types
4. Duplicate events are dropped (see below), then counters are incremented per matching event
5. On app foreground, the lifecycle observer checks if any pixel periods have elapsed
6. If elapsed, a pixel is fired with bucketed parameter values and an `attributionPeriod` timestamp

## Remote config parsing
- Only `period` triggers (an absent `type` means `period`) with `counter` parameters are supported. Entries with any other trigger type, such as `immediate` or `immediate_v2`, are skipped.
- Each `telemetry` entry is parsed on its own, so an entry this client can't parse is skipped and logged without affecting the others.

## De-duplication
- Each web event is checked once, before any counter sees it. The key is the event `type` plus its `data`, per tab. `data` objects that are equal as JSON values, in any member order, are one key, and an omitted `data` is the same as `{}`.
- A duplicate on the tab's current page reaches no counter. The first occurrence reaches every pixel watching that event, and uses up the key even if no pixel is counting at that moment.
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
