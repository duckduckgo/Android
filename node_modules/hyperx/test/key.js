var test = require('tape')
var vdom = require('virtual-dom')
var hyperx = require('../')
var hx = hyperx(vdom.h)

test('key', function (t) {
  var key = 'type'
  var value = 'text'
  var tree = hx`<input ${key}=${value}>`
  t.equal(vdom.create(tree).toString(), '<input type="text" />')
  t.end()
})

test('pre key', function (t) {
  var key = 'ype'
  var value = 'text'
  var tree = hx`<input t${key}=${value}>`
  t.equal(vdom.create(tree).toString(), '<input type="text" />')
  t.end()
})

test('post key', function (t) {
  var key = 'typ'
  var value = 'text'
  var tree = hx`<input ${key}e=${value}>`
  t.equal(vdom.create(tree).toString(), '<input type="text" />')
  t.end()
})

test('pre post key', function (t) {
  var key = 'yp'
  var value = 'text'
  var tree = hx`<input t${key}e=${value}>`
  t.equal(vdom.create(tree).toString(), '<input type="text" />')
  t.end()
})

test('boolean key', function (t) {
  var key = 'checked'
  var tree = hx`<input type="checkbox" ${key}>`
  t.equal(vdom.create(tree).toString(),
    '<input type="checkbox" checked="checked" />')
  t.end()
})

test('multiple keys', function (t) {
  var props = {
    type: 'text',
    'data-special': 'true'
  }
  var key = 'data-'
  var value = 'bar'
  var tree = hx`<input ${props} ${key}foo=${value}>`
  t.equal(vdom.create(tree).toString(), '<input type="text" data-special="true" data-foo="bar" />')
  t.end()
})

test('multiple keys dont overwrite existing ones', function (t) {
  var props = {
    type: 'text'
  }
  var tree = hx`<input type="date" ${props}>`
  t.equal(vdom.create(tree).toString(), '<input type="date" />')
  t.end()
})

// https://github.com/choojs/hyperx/issues/55
test('unquoted key does not make void element eat adjacent elements', function (t) {
  var tree = hx`<span><input type=text>sometext</span>`
  t.equal(vdom.create(tree).toString(), '<span><input type="text" />sometext</span>')
  t.end()
})
