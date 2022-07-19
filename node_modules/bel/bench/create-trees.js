var bench = require('./bench')
var bel = require('../')
var vdom = require('virtual-dom')
var h = vdom.h

function raw (label, items) {
  var div = document.createElement('div')
  var h1 = document.createElement('h1')
  label = document.createTextNode(label)
  h1.appendChild(label)
  var ul = document.createElement('ul')
  items.forEach(function (item) {
    var li = document.createElement('li')
    item = document.createTextNode(item)
    li.appendChild(item)
    ul.appendChild(li)
  })
  div.appendChild(h1)
  div.appendChild(ul)
  return div
}

function withBel (label, items) {
  return bel`<div>
    <h1>${label}</h1>
    <ul>
      ${items.map(function (item) {
        return bel`<li>${item}</li>`
      })}
    </ul>
  </div>`
}

function withVDOM (label, items) {
  return h('div', [
    h('h1', label),
    h('ul', items.map(function (item) {
      return h('li', item)
    }))
  ])
}

console.log('Creating trees...')
var data = ['grizzly', 'polar', 'brown']
bench('raw', function () {
  raw('test', data)
})
bench('withVDOM', function () {
  withVDOM('test', data)
})
bench('withBel', function () {
  withBel('test', data)
})
