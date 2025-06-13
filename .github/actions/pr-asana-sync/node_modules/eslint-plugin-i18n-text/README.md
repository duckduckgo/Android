# eslint-plugin-i18n-text

Disallow English text in string literals.

Embedding messages in JavaScript files prevents them from being translated into
other languages. An alternative is to embed the translated text in the markup
and find it with JavaScript.

```html
<div class="js-message" data-success-message="It works!">…</div>
```

```js
const el = document.querySelector('.js-message')
el.textContent = el.getAttribute('data-success-message')
```

This pattern allows the web framework that's generating the markup to use
its translation library to insert the appropriate translated text.

## Installation

You'll first need to install [ESLint](http://eslint.org):

```
$ npm install eslint --save-dev
```

Next, install `eslint-plugin-i18n-text`:

```
$ npm install eslint-plugin-i18n-text --save-dev
```

**Note:** If you installed ESLint globally (using the `-g` flag) then you must also install `eslint-plugin-i18n-text` globally.

## Usage

Add `i18n-text` to the plugins section of your `.eslintrc` configuration file. You can omit the `eslint-plugin-` prefix:

```json
{
  "plugins": [
    "i18n-text"
  ],
  "rules": {
    "i18n-text/no-en": 2
  }
}
```

## Development

```
npm install
npm test
```

## License

Distributed under the MIT license. See LICENSE for details.
