var React = require('react')
var toString = require('react-dom/server').renderToString
var hyperx = require('../')

// hyperx always uses a children array, but react needs children as separate arguments,
// so spread them using this wrapper
function createElement (tag, props, children) {
  return React.createElement.apply(null, [tag, props].concat(children))
}

function createFragment (children) {
  return createElement(React.Fragment, {}, children)
}

var hx = hyperx(createElement, {
  createFragment: createFragment
})

// use custom components by doing `<${Component}>`
function FancyTable (props) {
  return hx`
    <table class=fancy>
      ${props.children}
    </table>
  `
}

var title = 'world'
var wow = [1,2,3]
var frag = hx`
  <tr> <td>row1</td> </tr>
  <tr> <td>row2</td> </tr>
`
var tree = hx`<div>
  <h1 y="ab${1+2}cd">hello ${title}!</h1>
  ${hx`<i>cool</i>`}
  wow
  ${wow.map(function (w, i) {
    return hx`<b key="${i}">${w}</b>\n`
  })}

  <${FancyTable}>
    ${frag}
  </${FancyTable}>
</div>`
console.log(toString(tree))
