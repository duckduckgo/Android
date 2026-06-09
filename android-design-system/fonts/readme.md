# Fonts
Fallback font module providing default fonts as a substitute when the proprietary DuckSans fonts are unavailable.

This module is a resource-compatible shim — it provides the same `R.font.*` resource IDs as the proprietary `ddg-proprietary-fonts` AAR (e.g., `ducksans_display`, `ducksansdisplay_bold`) but with empty `<font-family/>` definitions that resolve to the platform's default font. This allows the rest of the codebase to reference DuckSans font resources without conditional compilation.

The build automatically swaps this module for the `ddg-proprietary-fonts` AAR when GitHub Packages credentials are detected (see `build.gradle`).

## Who can help you better understand this feature?
- Mike Scamell
- Ana Capatina

## More information
For credential setup instructions and architecture details, see the [Asana task](https://app.asana.com/1/137249556945/project/1207908166761516/task/1213686706814504).
