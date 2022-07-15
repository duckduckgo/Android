var vdom = require('virtual-dom')
var hyperx = require('../')
var hx = hyperx(vdom.h)

function render (state) {
  return hx`<div>
    <h1 y="ab${1+2}cd">hello ${state.title}!</h1>
    <i>cool</i>
    wow
    ${state.wow.map(function (w, i) {
      return hx`<b>${w}</b>\n`
    })}
  </div>`
}

var start = Date.now()
var N = 10000
for (var i = 0; i < N; i++) {
  render({ title: 'hello' + i, wow: [1,2,3] })
}
var elapsed = Date.now() - start
console.log(elapsed / N, 'ms')
