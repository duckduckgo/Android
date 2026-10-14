# Event Hub
Handles web events from Content Scope Scripts (C-S-S) via the eventHub feature, aggregating counters and firing bucketed pixels based on remote configuration.

## Architecture
- **event-hub-impl**: Feature plugins, config parser, pixel manager, JS message handler, pixel firing, Room database for pixel state

## How it works
1. Remote config defines pixel telemetry under the `eventHub` feature
2. The `webEvents` CSS feature routes `webEvent` messages from CSS to native
3. Each pixel parameter has a `source` that matches incoming event types
4. Counters are incremented per matching event
5. On app foreground, the lifecycle observer checks if any pixel periods have elapsed
6. If elapsed, a pixel is fired with bucketed parameter values and an `attributionPeriod` timestamp

## Remote config parsing
- Only `period` triggers (an absent `type` means `period`) with `counter` parameters are supported. Entries with any other trigger type, such as `immediate` or `immediate_v2`, are skipped.
- Each `telemetry` entry is parsed on its own, so an entry this client can't parse is skipped and logged without affecting the others.
