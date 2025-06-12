# browserslist-config

The GitHub browserslist config.

## How is this used on GitHub?

We use `browserslist` to power tools that guide developers to use APIs and syntax that is implemented by the [browsers we support](https://github.com/github/browser-support). These tools include [eslint-plugin-compat](https://github.com/amilajack/eslint-plugin-compat), [eslint-plugin-escompat](https://github.com/keithamus/eslint-plugin-escompat) and [postcss-preset-env](https://github.com/csstools/postcss-plugins/tree/main/plugin-packs/postcss-preset-env).

## Usage

Install the package using `npm`.

```sh
npm install --save-dev @github/browserslist-config
```

Add the `browserslist` key to your `package.json`.

```diff
diff --git a/package.json b/package.json
index 2ecef3d..260838f 100644
--- a/package.json
+++ b/package.json
@@ -5,6 +5,7 @@
   "keywords": [
     "prettier"
   ],
+  "browserslist": "extends @github/browserslist-config",
   "license": "MIT",
   "author": "GitHub Inc.",
   "main": "index.js"
 ```
 
[Check out the `browserslist` documentation for more info on sharing configurations](https://github.com/browserslist/browserslist#shareable-configs).

## Contributing

Contributing should only be done by GitHub staff and PRs are approved by `@github/web-systems` as the CODEOWNERS of this library. GitHub Staff can use [github/browser-support-cli](https://github.com/github/browser-support-cli) to get production data on browser usage for github.com.

## License

Distributed under the MIT license. See LICENSE for details.
