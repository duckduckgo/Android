# Web Telemetry
Handles telemetry events from Content Scope Scripts (C-S-S) by aggregating counters and firing bucketed pixels based on remote configuration.

## Architecture
- **web-telemetry-store**: Room database for config persistence and counter state
- **web-telemetry-impl**: Feature plugin, config parser, counter manager, JS message handler, pixel firing

## How it works
1. Remote config defines telemetry types with templates (currently: `counter`)
2. C-S-S fires `fireTelemetry` messages with a `type` parameter
3. The native handler increments per-type counters
4. On app foreground, the lifecycle observer checks if the configured period (day/week) has elapsed
5. If elapsed, a pixel is fired with the count mapped to a bucket, and the counter resets
