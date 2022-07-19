# [bel](https://en.wikipedia.org/wiki/Bel_(mythology))

[![NPM version][npm-image]][npm-url]
[![build status][travis-image]][travis-url]
[![Downloads][downloads-image]][downloads-url]
[![js-standard-style][standard-image]][standard-url]
![experimental][experimental-image]

A simple library for composable DOM elements using [tagged template strings](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Template_literals).

If you're looking for a higher level front end framework, try
[yo-yo](https://github.com/maxogden/yo-yo). Or even higher than that, try
[choo](https://github.com/yoshuawuyts/choo).

## usage

For a more in depth tutorial on getting started, please [check out the wiki](https://github.com/shama/bel/wiki).

### A Simple Element

Create an element:

```js
// list.js
var bel = require('bel')

module.exports = function (items) {
  return bel`<ul>
    ${items.map(function (item) {
      return bel`<li>${item}</li>`
    })}
  </ul>`
}
```

Then pass data to it and add to the DOM:

```js
// app.js
var createList = require('./list.js')
var list = createList([
  'grizzly',
  'polar',
  'brown'
])
document.body.appendChild(list)
```

### Data Down, Actions Up

```js
// list.js
var bel = require('bel')

// The DOM is built by the data passed in
module.exports = function (items, onselected) {
  function render () {
    return bel`<ul>
    ${items.map(function (item) {
      return bel`<li>${button(item.id, item.label)}</li>`
    })}
    </ul>`
  }
  function button (id, label) {
    return bel`<button onclick=${function () {
      // Then action gets sent up
      onselected(id)
    }}>${label}</button>`
  }
  var element = render()
  return element
}
```

```js
// app.js
var bel = require('bel')
var morphdom = require('morphdom')
var list = require('./list.js')

module.exports = function (bears) {
  function onselected (id) {
    // When a bear is selected, rerender with the newly selected item
    // This will use DOM diffing to render, sending the data back down again
    morphdom(element, render(id))
  }
  function render (selected) {
    return bel`<div className="app">
      <h1>Selected: ${selected}</h1>
      ${list(bears, onselected)}
    </div>`
  }
  // On first render, we haven't selected anything
  var element = render('none')
  return element
}
```

### use with/without [hyperx](https://www.npmjs.com/package/hyperx)

`hyperx` is built into `bel` but there may be times when you wish to use your
own version or implementation of `hyperx`. Or if you prefer to create elements
using `bel` without using tagged template literals:

```js
var createElement = require('bel').createElement
var hyperx = require('hyperx')
var bel = hyperx(createElement)

var element = bel`<div class="heading">Hello!</div>`

// ...

var sameElement = createElement('div', { className: 'heading' }, ['Hello!'])
```

### use yo-yoify to build

Transform bel template strings into pure and fast document calls with browserify.

e.g. `browserify entry.js -g yo-yoify -o bundle.js`

[See also](https://github.com/shama/yo-yoify#how-this-works)


## note

Please use [yo-yoify](https://github.com/shama/yo-yoify) which will transform any `Function.caller` into plain strings until an alternative solution to identify element creators is implemented.

yo-yoify can resolve the error like below:

`TypeError: Function.caller used to retrieve strict caller`

or

`TypeError: access to strict mode caller function is censored`


## security

bel sets attributes with `element.setAttribute()` and `element.setAttributeNS()`, and creates text nodes with `document.createTextNode()`.  These approaches mitigate some [Cross-Site Scripting (XSS)](https://www.owasp.org/index.php/Cross-site_Scripting_%28XSS%29) attacks.  You should still code carefully every time you put content from users in the DOM.

## unescaping

bel escapes `${values}` within template literals. Sometimes that is not desirable; for instance, when parsing a string with markdown, which returns HTML.

To unescape values, use the `raw` method:

```js
var bel = require('bel')
var raw = require('bel/raw')

function example () {
  var output = '<strong>hello there</strong>'
  return bel`
    <div>${raw(output)}</div>
  `
}

```

Make sure that you are sticking to the security suggestions above, and sanitize any input for malicious code before using `raw`.

## similar projects

* [vel](https://github.com/yoshuawuyts/vel)
  minimal virtual-dom library
* [base-element](https://github.com/shama/base-element)
  An element authoring library for creating standalone and performant elements
* [virtual-dom](https://github.com/Matt-Esch/virtual-dom)
  A Virtual DOM and diffing algorithm
* [hyperscript](https://github.com/dominictarr/hyperscript)
  Create HyperText with JavaScript.

# license
(c) 2016 Kyle Robinson Young. MIT License

[npm-image]: https://img.shields.io/npm/v/bel.svg?style=flat-square
[npm-url]: https://npmjs.org/package/bel
[travis-image]: https://img.shields.io/travis/shama/bel/master.svg?style=flat-square
[travis-url]: https://travis-ci.org/shama/bel
[downloads-image]: http://img.shields.io/npm/dm/bel.svg?style=flat-square
[downloads-url]: https://npmjs.org/package/bel
[standard-image]: https://img.shields.io/badge/code%20style-standard-brightgreen.svg?style=flat-square
[standard-url]: https://github.com/feross/standard
[experimental-image]: https://img.shields.io/badge/stability-experimental-orange.svg?style=flat-square
