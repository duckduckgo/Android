var test = require('tape')
var vdom = require('virtual-dom')
var hyperx = require('../')
var hx = hyperx(vdom.h)

test('class to className', function (t) {
  var tree = hx`<div class="wow"></div>`
  t.deepEqual(tree.properties, { className: 'wow' })
  t.end()
})

test('for to htmlFor', function (t) {
  var tree = hx`<div for="wow"></div>`
  t.deepEqual(tree.properties, { htmlFor: 'wow' })
  t.end()
})

test('http-equiv to httpEquiv', function (t) {
  var tree = hx`<meta http-equiv="refresh" content="30">`
  t.deepEqual(tree.properties, { content: '30', httpEquiv: 'refresh' })
  t.end()
})

test('no transform', t => {
  var hx = hyperx(vdom.h, { attrToProp: false })
  var tree = hx`<div class="wow"></div>`
  t.deepEqual(tree.properties, { class: 'wow' })
  t.end()
})
