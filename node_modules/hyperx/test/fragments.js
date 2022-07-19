var test = require('tape')
var vdom = require('virtual-dom')
var hyperx = require('../')
var hx = hyperx(vdom.h, {createFragment: createFragment})

function createFragment (nodes) {
  return nodes
}

test('mutliple root, fragments as array', function (t) {
  var list = hx`<li>1</li>  <li>2<div>_</div></li>`
  t.equal(list.length, 3, '3 elements')
  t.equal(vdom.create(list[0]).toString(), '<li>1</li>')
  t.equal(list[1], '  ')
  t.equal(vdom.create(list[2]).toString(), '<li>2<div>_</div></li>')
  t.end()
})
