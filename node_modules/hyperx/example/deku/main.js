var deku = require('deku')
var hyperx = require('../..')
var hx = hyperx(deku.element)

var render = deku.createApp(document.querySelector('#content'))

var state = {
  times: 0
}

rerender()

function rerender () {
  render(hx`<div>
    <h1>clicked ${state.times} times</h1>
    <button onClick=${increment}>click me!</button>
  </div>`)

  function increment () {
    state.times += 1
    rerender()
  }
}
