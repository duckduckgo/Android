var test = require('tape')
var vdom = require('virtual-dom')
var hyperx = require('../')
var hx = hyperx(vdom.h)

test('ignore whitespace surrounding an element', function (t) {
  var tree = hx`<div></div>`;
  t.equal(vdom.create(tree).toString(), '<div></div>')
  tree = hx`
    <div></div>`;
  t.equal(vdom.create(tree).toString(), '<div></div>')
  tree = hx`
    <div></div>
  `;
  t.equal(vdom.create(tree).toString(), '<div></div>')
  // It shouldn't strip whitespace from a text node
  t.equal(hx`  hello world  `, '  hello world  ')
  t.end()
})
