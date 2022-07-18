var test = require('tape')
var vdom = require('virtual-dom')
var hyperx = require('../')
var hx = hyperx(vdom.h)

test('title html tag', function (t) {
  var tree = hx`<title>hello</title>`
  t.equal(vdom.create(tree).toString(), '<title>hello</title>')
  t.end()
})
