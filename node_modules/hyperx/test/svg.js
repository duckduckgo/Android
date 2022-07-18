var test = require('tape')
var vdom = require('virtual-dom')
var hyperx = require('../')
var hx = hyperx(vdom.h)

test('svg mixed with html', function (t) {
  var expected = `<div>
    <h3>test</h3>
    <svg width="150" height="100" viewBox="0 0 3 2">
      <rect width="1" height="2" x="0" fill="#008d46"></rect>
    </svg>
  </div>`
  var tree = hx`<div>
    <h3>test</h3>
    <svg width="150" height="100" viewBox="0 0 3 2">
      <rect width="1" height="2" x="0" fill="#008d46" />
    </svg>
  </div>`
  t.equal(vdom.create(tree).toString(), expected)
  t.end()
})
