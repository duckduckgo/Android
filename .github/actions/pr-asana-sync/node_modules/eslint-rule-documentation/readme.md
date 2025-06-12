# eslint-rule-documentation [![Build Status](https://travis-ci.org/jfmengels/eslint-rule-documentation.svg?branch=master)](https://travis-ci.org/jfmengels/eslint-rule-documentation)

> Find the url for the documentation of an [ESLint] rule


## Install

```
$ npm install --save eslint-rule-documentation
```


## Usage

```js
const getRuleURI = require('eslint-rule-documentation');

// find url for core rules
getRuleURI('no-var');
// => { found: true, url: 'https://eslint.org/docs/rules/no-var' }

// find url for known plugins
getRuleURI('import/no-unresolved');
// => { found: true, url: 'https://github.com/benmosher/eslint-plugin-import/blob/master/docs/rules/no-unresolved.md' }

// If the plugin is not known, get a link to help improve this
getRuleURI('unknown-foo/bar');
// => { found: false, url: 'https://github.com/jfmengels/eslint-rule-documentation/blob/master/contributing.md' }
```

## Contributing

If you find a plugin that you use is not in the [list of supported plugins](./plugins.json), please consider adding it to the project by following the instructions [here](./contributing.md).


## API

### getRuleURI(ruleId)



#### ruleId

Type: `string`

Id of an [ESLint] rule.

Examples:
- core rule: `no-var`
- plugin rule: `import/no-unresolved` (from the [eslint-plugin-import] plugin).

#### returns

Type: `object`

```js
{
  found: <boolean>,
  url: <string>
}
```

- `found`: `true` if the rule is an ESLint core rule, or a rule of a known plugin, `false` otherwise.
- `url`: if `found` is `true`, url of the documentation of a rule. If `found` is `false`, url of the [contribution guidelines](./contributing.md).

## Thanks

Special thanks to the team behind [linter-eslint] for the original work, and the people who contributed there.


## License

MIT © [Jeroen Engels](https://github.com/jfmengels)

[eslint-plugin-import]: https://github.com/benmosher/eslint-plugin-import
[ESLint]: https://eslint.org/
[linter-eslint]: https://github.com/AtomLinter/linter-eslint
