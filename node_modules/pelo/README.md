# pelo (work in progress)

Lightning fast server-side rendering with tagged template literals

A tiny library that enables lightning fast server-side rendering with [hyperx](https://github.com/substack/hyperx)-like libraries such as `bel`, `yo-yo` and `choo/html`. It replaces the tag function of those libraries and just renders string without creating intermediate objects.

## Installing

```sh
npm install pelo
```

## Usage

`ssr.js`: Call `pelo.replace(moduleId)` before you require any view module, `bel` in this case.

```js
const pelo = require('pelo')
pelo.replace('bel')
const view = require('./view')

const renderedString = view('pelo').toString()
```

`view.js`: You don't need to change your view files at all. You can use them for client-side rendering and server-side rendering.

```js
const html = require('bel')

module.exports = function helloView(name) {
  return html`<p>Hello, ${name}</p>`
}
```

## Benchmark

Rendering a simple view 10,000 times:

```js
node benchmark.js
```

|  tag | time (ms) |
| ---- | --------- |
| pelo |   219.093 |
|  bel |  1982.610 |

## Motivation

Server-side rendering with modern JavaScript frameworks is slow. In general, they focus on the client-side, and generate virtual/real DOMs for efficient DOM updates from templates. However, this approach is a bit overkill when we focus on server-side rendering. Because the templates already look like HTML, it should be faster if they directly render HTML strings without creating intermediate object representations.

With [`bel`](https://github.com/shama/bel), we can write HTML with tagged template literals and use them to create declarative views on browser. If we can use the same template also for directly generating HTML string on server-side, it will be a huge win.

## Thanks

Thanks [@yoshuawuyts](https://github.com/yoshuawuyts) for lots of advice!
