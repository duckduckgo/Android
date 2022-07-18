var test = require('tape')
var vdom = require('virtual-dom')
var hyperx = require('../')
var hx = hyperx(vdom.h)

test('class', function (t) {
  var tree = hx`<div class="wow"></div>`
  t.equal(vdom.create(tree).toString(), '<div class="wow"></div>')
  t.end()
})

test('boolean attribute', function (t) {
  var tree = hx`<video autoplay></video>`
  t.equal(vdom.create(tree).toString(), '<video autoplay="autoplay"></video>')
  t.end()
})

test('boolean attribute followed by normal attribute', function (t) {
  var tree = hx`<video autoplay volume="50"></video>`
  t.equal(vdom.create(tree).toString(), '<video autoplay="autoplay" volume="50"></video>')
  t.end()
})

test('boolean attribute preceded by normal attribute', function (t) {
  var tree = hx`<video volume="50" autoplay></video>`
  t.equal(vdom.create(tree).toString(), '<video volume="50" autoplay="autoplay"></video>')
  t.end()
})

test('unquoted attribute', function (t) {
  var tree = hx`<div class=test></div>`
  t.equal(vdom.create(tree).toString(), '<div class="test"></div>')
  t.end()
})

test('unquoted attribute preceded by boolean attribute', function (t) {
  var tree = hx`<div hidden dir=ltr></div>`
  t.equal(vdom.create(tree).toString(), '<div hidden="hidden" dir="ltr"></div>')
  t.end()
})

test('unquoted attribute succeeded by boolean attribute', function (t) {
  var tree = hx`<div dir=ltr hidden></div>`
  t.equal(vdom.create(tree).toString(), '<div dir="ltr" hidden="hidden"></div>')
  t.end()
})

test('unquoted attribute preceded by normal attribute', function (t) {
  var tree = hx`<div id="test" class=test></div>`
  t.equal(vdom.create(tree).toString(), '<div id="test" class="test"></div>')
  t.end()
})

test('unquoted attribute succeeded by normal attribute', function (t) {
  var tree = hx`<div id=test class="test"></div>`
  t.equal(vdom.create(tree).toString(), '<div id="test" class="test"></div>')
  t.end()
})

test('consecutive unquoted attributes', function (t) {
  var tree = hx`<div id=test class=test></div>`
  t.equal(vdom.create(tree).toString(), '<div id="test" class="test"></div>')
  t.end()
})

test('strange leading character attributes', function (t) {
  var tree = hx`<div @click='test' :href='/foo'></div>`
  t.equal(vdom.create(tree).toString(), '<div @click="test" :href="/foo"></div>')
  t.end()
})

test('strange inbetween character attributes', function (t) {
  var tree = hx`<div f@o='bar' b&z='qux'></div>`
  t.equal(vdom.create(tree).toString(), `<div f@o="bar" b&z="qux"></div>`)
  t.end()
})

test('null and undefined attributes', function (t) {
  var tree = hx`<div onclick="alert(1)" onmouseenter=${undefined} onmouseleave=${null}></div>`
  t.equal(vdom.create(tree).toString(), `<div onclick="alert(1)"></div>`)
  t.end()
})

test('undefined (with quotes) attribute value is evaluated', function (t) {
  var tree = hx`<div foo='undefined'></div>`
  t.equal(vdom.create(tree).toString(), `<div foo="undefined"></div>`)
  t.end()
})

test('null (with quotes) attribute value is evaluated', function (t) {
  var tree = hx`<div foo='null'></div>`
  t.equal(vdom.create(tree).toString(), `<div foo="null"></div>`)
  t.end()
})

test('undefined (without quotes) attribute value is evaluated', function (t) {
  var tree = hx`<div foo=undefined></div>`
  t.equal(vdom.create(tree).toString(), `<div foo="undefined"></div>`)
  t.end()
})

test('null (without quotes) attribute value is evaluated', function (t) {
  var tree = hx`<div foo=null></div>`
  t.equal(vdom.create(tree).toString(), `<div foo="null"></div>`)
  t.end()
})

test('null is ignored and adjacent attribute is evaluated', function (t) {
  var tree = hx`<div foo=${null} t></div>`
  t.equal(vdom.create(tree).toString(), `<div t="t"></div>`)
  t.end()
})
