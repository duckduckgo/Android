var test = require('tape')
var vdom = require('virtual-dom')
var hyperx = require('../')
var hx = hyperx(function (tagName, opts, children) {
  return {
    expr: 'h(' + JSON.stringify(tagName)
      + ',' + JSON.stringify(opts)
      + ',[' + children.map(child).join(',')
      + '])'
  }
  function child (c) {
    if (c.expr) return c.expr
    else if (Array.isArray(c)) return c.map(child).join(',')
    else return JSON.stringify(c)
  }
}, { concat: concat })

function concat (a, b) {
  if (!a.expr && !b.expr) return String(a) + String(b)
  var aexpr, bexpr
  if (a.expr) aexpr = '(' + a.expr + ')'
  else aexpr = JSON.stringify(a)
  if (b.expr) bexpr = '(' + b.expr + ')'
  else bexpr = JSON.stringify(b)
  return { expr: aexpr + '+' + bexpr }
}

var expected = `<div>
    <h1 y="ab3cd">hello world!</h1>
    <i>cool</i>
    wow
    <b>1</b><b>2</b><b>3</b>
  </div>`

test('vdom', function (t) {
  var title = 'world'
  var wow = [1,2,3]
  var str = hx`<div>
    <h1 y="ab${1+2}cd">hello ${title}!</h1>
    ${hx`<i>cool</i>`}
    wow
    ${wow.map(function (w, i) {
      return hx`<b>${w}</b>\n`
    })}
  </div>`.expr
  var tree = Function(['h'],'return ' + str)(vdom.h)
  t.equal(vdom.create(tree).toString(), expected)
  t.end()
})
