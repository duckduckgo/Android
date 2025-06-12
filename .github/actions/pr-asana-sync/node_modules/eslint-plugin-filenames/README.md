# eslint-plugin-filenames

[![NPM Version](https://img.shields.io/npm/v/eslint-plugin-filenames.svg?style=flat-square)](https://www.npmjs.org/package/eslint-plugin-filenames)
[![Build Status](https://img.shields.io/travis/selaux/eslint-plugin-filenames.svg?style=flat-square)](https://travis-ci.org/selaux/eslint-plugin-filenames)
[![Coverage Status](https://img.shields.io/coveralls/selaux/eslint-plugin-filenames.svg?style=flat-square)](https://coveralls.io/r/selaux/eslint-plugin-filenames?branch=master)
[![Dependencies](https://img.shields.io/david/selaux/eslint-plugin-filenames.svg?style=flat-square)](https://david-dm.org/selaux/eslint-plugin-filenames)

Adds [eslint](http://eslint.org/) rules to ensure consistent filenames for your javascript files.

__Please note__: This plugin will only lint the filenames of the `.js`, `.jsx` files you are linting with eslint. It will ignore other files that are not linted with eslint.

## Enabling the plugin

This plugin requires a version of `eslint>=1.0.0` to be installed as a peer dependency.

Modify your `.eslintrc` file to load the plugin and enable the rules you want to use.

```json
{
  "plugins": [
    "filenames"
  ],
  "rules": {
    "filenames/match-regex": 2,
    "filenames/match-exported": 2,
    "filenames/no-index": 2
  }
}
```

## Rules

### Consistent Filenames via regex (match-regex)

A rule to enforce a certain file naming convention using a regular expression.

The convention can be configured using a regular expression (the default is `camelCase.js`). Additionally
exporting files can be ignored with a second configuration parameter.

```json
"filenames/match-regex": [2, "^[a-z_]+$", true]
```

With these configuration options, `camelCase.js` will be reported as an error while `snake_case.js` will pass.
Additionally the files that have a named default export (according to the logic in the `match-exported` rule) will be
ignored.  They could be linted with the `match-exported` rule. Please note that exported function calls are not
respected in this case.

### Matching Exported Values (match-exported)

Match the file name against the default exported value in the module. Files that dont have a default export will
be ignored. The exports of `index.js` are matched against their parent directory.

```js
// Considered problem only if the file isn't named foo.js or foo/index.js
export default function foo() {}

// Considered problem only if the file isn't named Foo.js or Foo/index.js
module.exports = class Foo() {}

// Considered problem only if the file isn't named someVariable.js or someVariable/index.js
module.exports = someVariable;

// Never considered a problem
export default { foo: "bar" };
```

If your filename policy doesn't quite match with your variable naming policy, you can add one or multiple transforms:

```json
"filenames/match-exported": [ 2, "kebab" ]
```

Now, in your code:

```js
// Considered problem only if file isn't named variable-name.js or variable-name/index.js
export default function variableName;
```

Available transforms:
'[snake](https://www.npmjs.com/package/lodash.snakecase)',
'[kebab](https://www.npmjs.com/package/lodash.kebabcase)',
'[camel](https://www.npmjs.com/package/lodash.camelcase)', and
'pascal' (camel-cased with first letter in upper case).

For multiple transforms simply specify an array like this (null in this case stands for no transform):

```json
"filenames/match-exported": [2, [ null, "kebab", "snake" ] ]
```

If you prefer to use suffixes for your files (e.g. `Foo.react.js` for a React component file),
you can use a second configuration parameter. It allows you to remove parts of a filename matching a regex pattern
before transforming and matching against the export.

```json
"filenames/match-exported": [ 2, null, "\\.react$" ]
```

Now, in your code:

```js
// Considered problem only if file isn't named variableName.react.js, variableName.js or variableName/index.js
export default function variableName;
```

If you also want to match exported function calls you can use the third option (a boolean flag).

```json
"filenames/match-exported": [ 2, null, null, true ]
```

Now, in your code:

```js
// Considered problem only if file isn't named functionName.js or functionName/index.js
export default functionName();
```

### Don't allow index.js files (no-index)

Having a bunch of `index.js` files can have negative influence on developer experience, e.g. when
opening files by name. When enabling this rule. `index.js` files will always be considered a problem.

## Changelog

#### 1.3.2

- Fix issue with `match-regex` and `getExportedName`

#### 1.3.1

- Put breaking change from `1.3.0` behind a flag

#### 1.3.0

- Support call expressions as named exports

#### 1.2.0
- Introduce `strip` option for `match-exported`
- Introduce support for multiple transform options
- Introduce `pascal` transform

#### 1.1.0
- Introduce `transform` option for `match-exported`

#### 1.0.0
- Split rule into `match-regex`, `match-exported` and `no-index`

#### 0.2.0
- Add match-exported flags

#### 0.1.2
- Fix example in README

#### 0.1.1
- Fix: Text via stdin always passes
- Tests: Travis builds also run on node 0.12 and iojs now

#### 0.1.0
- Initial Release
