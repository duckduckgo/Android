# svg-element-attributes

[![Build][build-badge]][build]
[![Downloads][downloads-badge]][downloads]
[![Size][size-badge]][size]

Map of SVG elements to allowed attributes.
Also contains global attributes under `'*'`.

Includes attributes from [SVG 1.1][1.1], [SVG Tiny 1.2][1.2], and [SVG 2][2.0].

> **Note**: Does not include ARIA attributes (`role`, `aria-*`), `xml:*` or
> `xlink:*` attributes, event attributes (`on*`), or `ev:event`.

## Install

[npm][]:

```sh
npm install svg-element-attributes
```

## Use

```js
var svgElementAttributes = require('svg-element-attributes')

console.log(svgElementAttributes['*'])
console.log(svgElementAttributes.circle)
```

Yields:

```js
[ 'about',
  'class',
  'content',
  'datatype',
  'id',
  'lang',
  'property',
  'rel',
  'resource',
  'rev',
  'tabindex',
  'typeof' ]
[ 'alignment-baseline',
  'baseline-shift',
  'clip',
  'clip-path',
  'clip-rule',
  'color',
  'color-interpolation',
  'color-interpolation-filters',
  'color-profile',
  'color-rendering',
  'cursor',
  'cx',
  'cy',
  'direction',
  'display',
  'dominant-baseline',
  'enable-background',
  'externalResourcesRequired',
  'fill',
  'fill-opacity',
  'fill-rule',
  'filter',
  'flood-color',
  'flood-opacity',
  'focusHighlight',
  'focusable',
  'font-family',
  'font-size',
  'font-size-adjust',
  'font-stretch',
  'font-style',
  'font-variant',
  'font-weight',
  'glyph-orientation-horizontal',
  'glyph-orientation-vertical',
  'image-rendering',
  'kerning',
  'letter-spacing',
  'lighting-color',
  'marker-end',
  'marker-mid',
  'marker-start',
  'mask',
  'nav-down',
  'nav-down-left',
  'nav-down-right',
  'nav-left',
  'nav-next',
  'nav-prev',
  'nav-right',
  'nav-up',
  'nav-up-left',
  'nav-up-right',
  'opacity',
  'overflow',
  'pathLength',
  'pointer-events',
  'r',
  'requiredExtensions',
  'requiredFeatures',
  'requiredFonts',
  'requiredFormats',
  'shape-rendering',
  'stop-color',
  'stop-opacity',
  'stroke',
  'stroke-dasharray',
  'stroke-dashoffset',
  'stroke-linecap',
  'stroke-linejoin',
  'stroke-miterlimit',
  'stroke-opacity',
  'stroke-width',
  'style',
  'systemLanguage',
  'text-anchor',
  'text-decoration',
  'text-rendering',
  'transform',
  'unicode-bidi',
  'visibility',
  'word-spacing',
  'writing-mode' ]
```

## API

### `svgElementAttributes`

`Object.<Array.<string>>` — Map of tag names to an array of attribute names.

The object contains one special key: `'*'`, which contains global attributes
that apply to all SVG elements.

## License

[MIT][license] © [Titus Wormer][author]

<!-- Definition -->

[build-badge]: https://img.shields.io/travis/wooorm/svg-element-attributes.svg

[build]: https://travis-ci.org/wooorm/svg-element-attributes

[downloads-badge]: https://img.shields.io/npm/dm/svg-element-attributes.svg

[downloads]: https://www.npmjs.com/package/svg-element-attributes

[size-badge]: https://img.shields.io/bundlephobia/minzip/svg-element-attributes.svg

[size]: https://bundlephobia.com/result?p=svg-element-attributes

[npm]: https://docs.npmjs.com/cli/install

[license]: license

[author]: https://wooorm.com

[1.1]: https://www.w3.org/TR/SVG/attindex.html

[1.2]: https://www.w3.org/TR/SVGTiny12/attributeTable.html

[2.0]: https://www.w3.org/TR/SVG2/attindex.html
