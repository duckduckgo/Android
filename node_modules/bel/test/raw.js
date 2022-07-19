var test = require('tape')
var bel = require('../')
var raw = require('../raw')

test('unescape html', function (t) {
  t.plan(1)

  var expected = bel`<span>Hello there</span>`.toString()
  var result = raw('<span>Hello&nbsp;there</span>').toString()

  t.equal(expected, result)
  t.end()
})
