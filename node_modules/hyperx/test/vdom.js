var test = require('tape')
var vdom = require('virtual-dom')
var hyperx = require('../')
var hx = hyperx(vdom.h)

var expected = `<div>
    <h1 y="ab3cd">hello world!</h1>
    <i>cool</i>
    wow
    <b>1</b><b>2</b><b>3</b>
  </div>`

test('vdom', function (t) {
  var title = 'world'
  var wow = [1,2,3]
  var tree = hx`<div>
    <h1 y="ab${1+2}cd">hello ${title}!</h1>
    ${hx`<i>cool</i>`}
    wow
    ${wow.map(function (w, i) {
      return hx`<b>${w}</b>\n`
    })}
  </div>`
  t.equal(vdom.create(tree).toString(), expected)
  t.end()
})
