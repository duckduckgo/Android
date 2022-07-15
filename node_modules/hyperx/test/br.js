var test = require('tape')
var vdom = require('virtual-dom')
var hyperx = require('../')
var hx = hyperx(vdom.h)

test('self closing tags without a space', function (t) {
  var tree = hx`<div>a<br/>b<img src="boop"/></div>`
  t.equal(vdom.create(tree).toString(), '<div>a<br />b<img src="boop" /></div>')
  t.end()
})
