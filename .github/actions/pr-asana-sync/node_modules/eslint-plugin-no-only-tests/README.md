# eslint-plugin-no-only-tests

[![Version](https://img.shields.io/npm/v/eslint-plugin-no-only-tests.svg)](https://www.npmjs.com/package/eslint-plugin-no-only-tests) [![Downloads](https://img.shields.io/npm/dm/eslint-plugin-no-only-tests.svg)](https://npmcharts.com/compare/eslint-plugin-no-only-tests?minimal=true) [![GitHub Tests](https://github.com/levibuzolic/eslint-plugin-no-only-tests/workflows/Tests/badge.svg)](https://github.com/levibuzolic/eslint-plugin-no-only-tests/actions?query=workflow%3ATests)

ESLint rule for `.only` tests in [Mocha](https://mochajs.org/), [Jest](https://jestjs.io/), [Jasmine](https://jasmine.github.io/), [Mocha Cakes 2](https://github.com/iensu/mocha-cakes-2) and other JS testing libraries.

The following test blocks are matched by default: `describe`, `it`, `context`, `tape`, `test`, `fixture`, `serial`, `Feature`, `Scenario`, `Given`, `And`, `When` and `Then`.

Designed to prevent you from committing focused (`.only`) tests to CI, which may prevent your entire test suite from running.

If the testing framework you use doesn't use `.only` to focus tests, you can override the matchers with [options](#options).

## Installation

[Install ESLint](https://eslint.org/docs/user-guide/getting-started) if you haven't done so already, then install `eslint-plugin-no-only-tests`:

```bash
npm install --save-dev eslint-plugin-no-only-tests
# or
yarn add --dev eslint-plugin-no-only-tests
```

> **Note:** If you installed ESLint globally (using the `-g` flag) then you must also install `eslint-plugin-no-only-tests` globally.

## Usage

Add `no-only-tests` to the plugins section of your `.eslintrc` configuration file. You can omit the `eslint-plugin-` prefix:

```json
"plugins": [
  "no-only-tests"
]
```

Then add the rule to the rules section of your `.eslintrc`:

```json
"rules": {
  "no-only-tests/no-only-tests": "error"
}
```

If you use a testing framework that uses a test block name that isn't present in the [defaults](#options), or a different way of focusing test (something other than `.only`) you can specify an array of blocks and focus methods to match in the options.

```json
"rules": {
  "no-only-tests/no-only-tests": [
    "error", {
      "block": ["test", "it", "assert"],
      "focus": ["only", "focus"]
    }
  ]
}
```

The above example will catch any uses of `test.only`, `test.focus`, `it.only`, `it.focus`, `assert.only` and `assert.focus`.

This rule supports opt-in autofixing when the `fix` option is set to `true` to avoid changing runtime code unintentionally when configured in an editor.

```json
"rules": {
  "no-only-tests/no-only-tests": ["error", {"fix": true}]
}
```

## Options

| Option      | Type       | Description                                                                                                                                                                                                                                                                                                   |
| ----------- | ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `block`     | `string[]` | Specify the block names that your testing framework uses. Add a `*` to the end of any string to enable prefix matching (ie. `test*` will match `testExample.only`)<br>Defaults to `["describe", "it", "context", "test", "tape", "fixture", "serial", "Feature", "Scenario", "Given", "And", "When", "Then"]` |
| `focus`     | `string[]` | Specify the focus scope that your testing framework uses.<br>Defaults to `["only"]`                                                                                                                                                                                                                           |
| `functions` | `string[]` | Specify not permitted functions. Good examples are `fit` or `xit`.<br>Defaults to `[]` (disabled)                                                                                                                                                                                                             |
| `fix`       | `boolean`  | Enable this rule to auto-fix violations, useful for a pre-commit hook, not recommended for users with auto-fixing enabled in their editor.<br>Defaults to `false`                                                                                                                                             |
