var test = require('tape')
var vdom = require('virtual-dom')
var hyperx = require('../')
var hx = hyperx(vdom.h)

test('1 child', function (t) {
  var tree = hx`<div><span>foobar</span></div>`
  t.equal(vdom.create(tree).toString(), '<div><span>foobar</span></div>')
  t.end()
})

test('no children', function (t) {
  var tree = hx`<img href="xxx">`
  t.equal(vdom.create(tree).toString(), '<img href="xxx" />')
  t.end()
})

test('multiple children', function (t) {
  var html = `<div>
    <h1>title</h1>
    <div>
      <ul>
        <li>
          <a href="#">click</a>
        </li>
      </ul>
    </div>
  </div>`
  var tree = hx`
  <div>
    <h1>title</h1>
    <div>
      <ul>
        <li>
          <a href="#">click</a>
        </li>
      </ul>
    </div>
  </div>`
  t.equal(vdom.create(tree).toString(), html)
  t.end()
})
