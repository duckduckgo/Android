# Screenshot library comparison scripts

Requires Python 3 with `pillow` and `numpy` (for example in a venv: `pip install pillow numpy`).

- `gen_catalog.py <tests-dir> <catalog-dir>`: generated `src/screenshotCatalog/java` from the Compose Preview branch's per-component tests. One-off; it isn't needed again unless the catalog is regenerated.
- `collect.py <module-dir>`: finds each library's recorded image per case and prints their sizes.
- `compare.py <module-dir> <out.html>`: compares the three libraries pixel by pixel and writes the HTML report plus a JSON summary next to it.

Record, then compare (from the repository root):

```bash
./gradlew -q :design-system:recordRoborazziDebug --tests '*RoborazziDesignSystemScreenshotTest'
./gradlew -q :design-system:recordPaparazziDebug --tests '*PaparazziDesignSystemScreenshotTest'
./gradlew -q :design-system:updateDebugScreenshotTestDefaultTestSuite
python3 screenshot-comparison-report/scripts/compare.py android-design-system/design-system screenshot-comparison-report/index.html
```

The "What the differences are" findings in `compare.py` are written by hand from the current images; update them if the results change.
