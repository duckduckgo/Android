var test = require('tape')
var vdom = require('virtual-dom')
var hyperx = require('../')
var hx = hyperx(vdom.h)

test('multiple element error', function (t) {
  t.plan(1)
  t.throws(function () {
    var tree = hx`<div>one</div><div>two</div>`
  }, 'exception')
  t.end()
})
