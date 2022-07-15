var test = require('tape')
var vdom = require('virtual-dom')
var hyperx = require('../')
var hx = hyperx(vdom.h)

test('style', function (t) {
  var key = 'type'
  var value = 'text'
  var tree = hx`<input style=${
    {color:'purple','font-size':16}
  } type="text">`
  t.equal(
    vdom.create(tree).toString(),
    '<input style="color:purple;font-size:16;" type="text" />'
  )
  t.end()
})
