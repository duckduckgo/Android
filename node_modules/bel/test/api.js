var test = require('tape')
var bel = require('../')

test('creates an element', function (t) {
  t.plan(3)
  var button = bel`
    <button onclick=${function () { onselected('success') }}>
      click me
    </button>
  `

  var result = bel`
    <ul>
      <li>${button}</li>
    </ul>
  `

  function onselected (result) {
    t.equal(result, 'success')
    t.end()
  }

  t.equal(result.tagName, 'UL')
  t.equal(result.querySelector('button').textContent, 'click me')

  button.click()
})

test('using class and className', function (t) {
  t.plan(2)
  var result = bel`<div className="test1"></div>`
  t.equal(result.className, 'test1')
  result = bel`<div class="test2 another"></div>`
  t.equal(result.className, 'test2 another')
  t.end()
})
