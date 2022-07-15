var test = require('tape')
var vdom = require('virtual-dom')
var hyperx = require('../')
var hx = hyperx(vdom.h)

test('undefined value (empty)', function (t) {
  var tree = hx`<div>${undefined}</div>`
  t.equal(vdom.create(tree).toString(), '<div></div>')
  t.end()
})

test('null value (empty)', function (t) {
  var tree = hx`<div>${null}</div>`
  t.equal(vdom.create(tree).toString(), '<div></div>')
  t.end()
})

test('boolean value', function (t) {
  var tree = hx`<div>${false}</div>`
  t.equal(vdom.create(tree).toString(), '<div>false</div>')
  t.end()
})

test('numeric value', function (t) {
  var tree = hx`<div>${555}</div>`
  t.equal(vdom.create(tree).toString(), '<div>555</div>')
  t.end()
})
