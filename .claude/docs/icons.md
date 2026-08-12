# Adding an icon

**Ask the user for the icon name, then fetch it from the internal DDG Icons repository at
https://dub.duckduckgo.com/duckduckgo/Icons.** Do not guess icon names and do not skip this step —
inventing a name produces an asset that doesn't match the design system.

- Keep the SVG file name as close to the original as possible, and import it as a vector asset
- An icon used by a single feature belongs in that feature's `-impl` module, not `common-ui`
- For an icon that must follow the theme, set `android:fillColor="?attr/daxColorPrimaryIcon"`
