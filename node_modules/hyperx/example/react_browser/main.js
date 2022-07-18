var React = require('react')
var render = require('react-dom').render
var hyperx = require('../../')
var hx = hyperx(React.createElement)

var App = React.createClass({
  getInitialState: function () { return { n: 0 } },
  render: function () {
    return hx`<div>
      <h1>clicked ${this.state.n} times</h1>
      <button onClick=${this.handleClick}>click me!</button>
    </div>`
  },
  handleClick: function () {
    this.setState({ n: this.state.n + 1 })
  }
})
render(React.createElement(App), document.querySelector('#content'))
