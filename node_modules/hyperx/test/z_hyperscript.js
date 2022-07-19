var test = require('tape')
var h = require('hyperscript')
var hyperx = require('../')
var hx = hyperx(h)

// We cant use custom attributes y="" with hyperscript in the browser, use data-y="" instead
var expected = `<div>
    <h1 data-y="ab3cd">hello world!</h1>
    <i>cool</i>
    wow
    <b>1</b><b>2</b><b>3</b>
  </div>`

test('hyperscript', function (t) {
  var title = 'world'
  var wow = [1,2,3]
  var tree = hx`<div>
    <h1 data-y="ab${1+2}cd">hello ${title}!</h1>
    ${hx`<i>cool</i>`}
    wow
    ${wow.map(function (w) {
      return hx`<b>${w}</b>\n`
    })}
  </div>`
  t.equal(tree.outerHTML, expected)
  t.end()
})
