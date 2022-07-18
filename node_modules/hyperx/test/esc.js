var test = require('tape')
var vdom = require('virtual-dom')
var hyperx = require('../')
var hx = hyperx(vdom.h)

test('escape double quotes', function (t) {
  var value = '">'
  var tree = hx`<input type="text" value="${value}"></h1>`
  t.equal(
    vdom.create(tree).toString(),
  `<input type="text" value="&quot;&gt;" />`
  )
  t.end()
})

test('escape single quotes', function (t) {
  var value = "'>"
  var tree = hx`<input type='text' value='${value}'></h1>`
  t.equal(
    vdom.create(tree).toString(),
  `<input type="text" value="'&gt;" />`
  )
  t.end()
})
