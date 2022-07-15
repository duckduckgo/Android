var vdom = require('virtual-dom')
var h = vdom.h

function render (state) {
  return h('div', [
    h('h1', { y: 'ab' + (1+2) + 'cd' }, [
      'hello ' + state.title + '!'
    ]),
    h('i', 'cool'),
    'wow'
  ].concat(state.wow.map(function (w, i) {
    return h('b', {}, w)
  })))
}

var start = Date.now()
var N = 10000
for (var i = 0; i < N; i++) {
  render({ title: 'hello' + i, wow: [1,2,3] })
}
var elapsed = Date.now() - start
console.log(elapsed / N, 'ms')
