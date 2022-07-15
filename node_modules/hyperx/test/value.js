var test = require('tape')
var vdom = require('virtual-dom')
var hyperx = require('../')
var hx = hyperx(vdom.h)

test('value', function (t) {
  var key = 'type'
  var value = 'text'
  var tree = hx`<input ${key}=${value}>`
  t.equal(vdom.create(tree).toString(), '<input type="text" />')
  t.end()
})

test('pre value', function (t) {
  var key = 'type'
  var value = 'ext'
  var tree = hx`<input ${key}=t${value}>`
  t.equal(vdom.create(tree).toString(), '<input type="text" />')
  t.end()
})

test('post key', function (t) {
  var key = 'type'
  var value = 'tex'
  var tree = hx`<input ${key}=${value}t>`
  t.equal(vdom.create(tree).toString(), '<input type="text" />')
  t.end()
})

test('pre post key', function (t) {
  var key = 'type'
  var value = 'ex'
  var tree = hx`<input ${key}=t${value}t>`
  t.equal(vdom.create(tree).toString(), '<input type="text" />')
  t.end()
})
