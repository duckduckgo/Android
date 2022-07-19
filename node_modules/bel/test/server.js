var test = require('tape')
var bel = require('../')
var raw = require('../raw')

test('server side render', function (t) {
  t.plan(2)
  var element = bel`<div class="testing">
    <h1>hello!</h1>
  </div>`
  var result = element.toString()
  t.ok(result.indexOf('<h1>hello!</h1>') !== -1, 'contains a child element')
  t.ok(result.indexOf('<div class="testing">') !== -1, 'attribute gets set')
  t.end()
})

test('passing another element to bel on server side render', function (t) {
  t.plan(1)
  var button = bel`<button>click</button>`
  var element = bel`<div class="testing">
    ${button}
  </div>`
  var result = element.toString()
  t.ok(result.indexOf('<button>click</button>') !== -1, 'button rendered correctly')
  t.end()
})

test('style attribute', function (t) {
  t.plan(1)
  var name = 'test'
  var result = bel`<h1 style="color: red">Hey ${name.toUpperCase()}, <span style="color: blue">This</span> is a card!!!</h1>`
  var expected = '<h1 style="color: red">Hey TEST, <span style="color: blue">This</span> is a card!!!</h1>'
  t.equal(result.toString(), expected)
  t.end()
})

test('unescape html', function (t) {
  t.plan(1)

  var expected = '<span>Hello <strong>there</strong></span>'
  var result = raw('<span>Hello <strong>there</strong></span>').toString()

  t.equal(expected, result)
  t.end()
})

test('unescape html inside bel', function (t) {
  t.plan(1)

  var expected = '<span>Hello <strong>there</strong></span>'
  var result = bel`${raw('<span>Hello <strong>there</strong></span>')}`.toString()

  t.equal(expected, result)
  t.end()
})
