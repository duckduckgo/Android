# Event Hub
Handles web events from Content Scope Scripts (C-S-S) via the eventHub feature, aggregating counters and firing bucketed pixels based on remote configuration.

## Architecture
- **event-hub-store**: Room database for eventHub config, webEvents CSS feature config, and pixel state
- **event-hub-impl**: Feature plugins, config parser, pixel manager, JS message handler, pixel firing

## How it works
1. Remote config defines pixel telemetry under the `eventHub` feature
2. The `webEvents` CSS feature routes `webEvent` messages from CSS to native
3. Each pixel parameter has a `source` that matches incoming event types
4. Counters are incremented per matching event
5. On app foreground, the lifecycle observer checks if any pixel periods have elapsed
6. If elapsed, a pixel is fired with bucketed parameter values and an `attributionPeriod` timestamp
