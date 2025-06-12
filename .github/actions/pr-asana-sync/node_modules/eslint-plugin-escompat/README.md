# eslint-plugin-escompat

This plugin will report eslint errors for code which - if left untranspiled -
will not work in some browsers.

This is useful if you intend to ship code without first using a transpiler, such
as [Babel](https://babeljs.io).

This _won't_ lint for features that can be polyfilled. For that you can use
[eslint-plugin-compat][epc].

## Installation

```bash
npm install --save-dev eslint-plugin-escompat
```

## Usage for Flat Configs (eslint.config.js) - ESLint >= 8

```js
// eslint.config.js

import globals from 'globals';
import escompat from 'eslint-plugin-escompat';

export default [
    {
        plugins: {
            escompat
        },
        languageOptions: {
            globals: globals.browser
        },
        rules: {
            // Configure the individual `"escompat/*"` rules, e.g.:
            'escompat/no-async-generator': ['error'],
            'escompat/no-numeric-separators': ['error']
        }
    }
];
```

Alternatively, you can use the `recommended` configuration which will do the
plugins for you, with all recommended `"escompat/*"` rules reporting errors.

```js
import globals from 'globals';
import escompat from 'eslint-plugin-escompat';

export default [
    {
        languageOptions: {
            globals: globals.browser
        }
    },
    escompat.configs['flat/recommended']
];
```


## Usage for .eslintrc configs - ESLint < 9

Add `"escompat"` to `.eslintrc` `"plugins"` section, add `"browser": true` to
`"env"`, then configure the individual `"escompat/*"` rules.

Alternatively, you can use the `recommended` configuration which will do this
for you, with the `"escompat/*"` rules reporting errors (as in the snippet
above).

```js
// .eslintrc
{
  "extends": ["plugin:escompat/recommended"]
}
```

### TypeScript Users

Aside from the `recommended` config, there are also multiple `typescript`
configs which can be used if you're using TypeScript. The TypeScript configs
only enable some of the rules, avoiding enabling rules for which `typescript`
safely transpiles down to a more compatible syntax. Extend the typescript config
that matches your `tsconfig.json` `target` value.

For flat configs:

```js
import globals from 'globals';
import escompat from 'eslint-plugin-escompat';

export default [
    {
        languageOptions: {
            globals: globals.browser
        }
    },

    // The TypeScript configs are in array form, so we need to
    //   spread them out here
    ...escompat.configs['flat/typescript-2016']
];
```

or for `.eslintrc`:

```js
{
  "extends": ["plugin:escompat/typescript-2016"]
}
```

## Targeting Browsers

`eslint-plugin-escompat` uses the `browserslist` configuration in `package.json`

If you have a browserslist, it is safe to enable all of these rules - as any that
do not coincide with your chosen browsers will be turned off automatically.

See [browserslist/browserslist](https://github.com/browserslist/browserslist)
for configuration. Here's some examples:

```js
// Simple configuration (package.json)
{
  // ...
  "browserslist": ["last 1 versions", "not ie <= 8"],
}
```

```js
// Use development and production configurations (package.json)
{
  // ...
  "browserslist": {
    "development": ["last 2 versions"],
    "production": ["last 4 versions"]
  }
}
```

:bulb: You can also define browsers in a
[separate browserslist file](https://github.com/browserslist/browserslist#config-file)

## Rules

- [no-async-generator](./docs/no-async-generator.md)
- [no-async-iteration](./docs/no-async-iteration.md)
- [no-bigint](./docs/no-bigint.md)
- [no-bind-operator](./docs/no-bind-operator.md)
- [no-class-static-blocks](./docs/no-class-static-blocks.md)
- [no-computed-public-class-fields](./docs/no-computed-public-class-fields.md)
- [no-do-expression](./docs/no-do-expression.md)
- [no-dynamic-imports](./docs/no-dynamic-imports.md)
- [no-edge-destructure-bug](./docs/no-edge-destructure-bug.md)
- [no-exponentiation-operator](./docs/no-exponentiation-operator.md)
- [no-hashbang-comment](./docs/no-hashbang-comment.md)
- [no-logical-assignment-operator](./docs/no-logical-assignment-operator.md)
- [no-nullish-coalescing](./docs/no-nullish-coalescing.md)
- [no-numeric-separators](./docs/no-numeric-separators.md)
- [no-object-rest-spread](./docs/no-object-rest-spread.md)
- [no-optional-catch](./docs/no-optional-catch.md)
- [no-optional-chaining](./docs/no-optional-chaining.md)
- [no-pipeline-operator](./docs/no-pipeline-operator.md)
- [no-private-class-fields](./docs/no-private-class-fields.md)
- [no-public-instance-class-fields](./docs/no-public-instance-class-fields.md)
- [no-public-static-class-fields](./docs/no-public-static-class-fields.md)
- [no-regexp-duplicate-named-groups](./docs/no-regexp-duplicate-named-groups.md)
- [no-regexp-lookbehind](./docs/no-regexp-lookbehind.md)
- [no-regexp-named-group](./docs/no-regexp-named-group.md)
- [no-regexp-s-flag](./docs/no-regexp-s-flag.md)
- [no-regexp-v-flag](./docs/no-regexp-v-flag.md)
- [no-top-level-await](./docs/no-top-level-await.md)

## Inspiration

This project was largely inspired by the great [eslint-plugin-compat][epc]
library.

[epc]: https://github.com/amilajack/eslint-plugin-compat
