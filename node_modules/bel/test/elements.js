var test = require('tape')
var bel = require('../')

test('create inputs', function (t) {
  t.plan(5)

  var expected = 'testing'
  var result = bel`<input type="text" value="${expected}" />`
  t.equal(result.tagName, 'INPUT', 'created an input')
  t.equal(result.value, expected, 'set the value of an input')

  result = bel`<input type="checkbox" checked="${true}" disabled="${false}" />`
  t.equal(result.getAttribute('type'), 'checkbox', 'created a checkbox')
  t.equal(result.getAttribute('checked'), 'checked', 'set the checked attribute')
  t.equal(result.getAttribute('disabled'), null, 'should not have set the disabled attribute')

  t.end()
})

test('can update and submit inputs', function (t) {
  t.plan(2)
  document.body.innerHTML = ''
  var expected = 'testing'
  function render (data, onsubmit) {
    var input = bel`<input type="text" value="${data}" />`
    return bel`<div>
      ${input}
      <button onclick=${function () {
        onsubmit(input.value)
      }}>submit</button>
    </div>`
  }
  var result = render(expected, function onsubmit (newvalue) {
    t.equal(newvalue, 'changed')
    document.body.innerHTML = ''
    t.end()
  })
  document.body.appendChild(result)
  t.equal(document.querySelector('input').value, expected, 'set the input correctly')
  document.querySelector('input').value = 'changed'
  document.querySelector('button').click()
})

test('svg', function (t) {
  t.plan(3)
  var result = bel`<svg width="150" height="100" viewBox="0 0 3 2">
    <rect width="1" height="2" x="0" fill="#008d46" />
    <use xlink:href="#test" />
  </svg>`
  t.equal(result.tagName, 'svg', 'create svg tag')
  t.equal(result.childNodes[0].tagName, 'rect', 'created child rect tag')
  t.equal(result.childNodes[1].getAttribute('xlink:href'), '#test', 'created child use tag with xlink:href')
  t.end()
})

test('svg with namespace', function (t) {
  t.plan(3)
  var result
  function create () {
    result = bel`<svg width="150" height="100" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 3 2">
      <rect width="1" height="2" x="0" fill="#008d46" />
    </svg>`
  }
  t.doesNotThrow(create)
  t.equal(result.tagName, 'svg', 'create svg tag')
  t.equal(result.childNodes[0].tagName, 'rect', 'created child rect tag')
})

test('svg with xmlns:svg', function (t) {
  t.plan(3)
  var result
  function create () {
    result = bel`<svg width="150" height="100" xmlns:svg="http://www.w3.org/2000/svg" viewBox="0 0 3 2">
      <rect width="1" height="2" x="0" fill="#008d46" />
    </svg>`
  }
  t.doesNotThrow(create)
  t.equal(result.tagName, 'svg', 'create svg tag')
  t.equal(result.childNodes[0].tagName, 'rect', 'created child rect tag')
})

test('comments', function (t) {
  var result = bel`<div><!-- this is a comment --></div>`
  t.equal(result.outerHTML, '<div><!-- this is a comment --></div>', 'created comment')
  t.end()
})

test('style', function (t) {
  t.plan(2)
  var name = 'test'
  var result = bel`<h1 style="color: red">Hey ${name.toUpperCase()}, <span style="color: blue">This</span> is a card!!!</h1>`
  t.equal(result.style.color, 'red', 'set style color on parent')
  t.equal(result.querySelector('span').style.color, 'blue', 'set style color on child')
  t.end()
})

test('adjacent text nodes', function (t) {
  t.plan(2)
  var who = 'world'
  var exclamation = ['!', ' :)']
  var result = bel`<div>hello ${who}${exclamation}</div>`
  t.equal(result.childNodes.length, 1, 'should be merged')
  t.equal(result.outerHTML, '<div>hello world! :)</div>', 'should have correct output')
  t.end()
})

test('space in only-child text nodes', function (t) {
  t.plan(1)
  var result = bel`
    <span>
      surrounding
      newlines
    </span>
  `
  t.equal(result.outerHTML, '<span>surrounding newlines</span>', 'should remove extra space')
  t.end()
})

test('space between text and non-text nodes', function (t) {
  t.plan(1)
  var result = bel`
    <p>
      <dfn>whitespace</dfn>
      is empty
    </p>
  `
  t.equal(result.outerHTML, '<p><dfn>whitespace</dfn> is empty</p>', 'should have correct output')
  t.end()
})

test('space between non-text nodes', function (t) {
  t.plan(1)
  var result = bel`
    <p>
      <dfn>whitespace</dfn>
      <em>is beautiful</em>
    </p>
  `
  t.equal(result.outerHTML, '<p><dfn>whitespace</dfn> <em>is beautiful</em></p>', 'should have correct output')
  t.end()
})

test('space in <pre>', function (t) {
  t.plan(1)
  var result = bel`
    <pre>
      whitespace is empty
    </pre>
  `
  t.equal(result.outerHTML, '<pre>\n      whitespace is empty\n    </pre>', 'should preserve space')
  t.end()
})

test('space in <textarea>', function (t) {
  t.plan(1)
  var result = bel`
    <textarea> Whitespace at beginning 
      middle
      and end </textarea>
  `
  t.equal(result.outerHTML, '<textarea> Whitespace at beginning \n      middle\n      and end </textarea>', 'should preserve space')
  t.end()
})

test('for attribute is set correctly', function (t) {
  t.plan(1)
  var result = bel`<div>
    <input type="file" name="file" id="heyo" />
    <label for="heyo">label</label>
  </div>`
  t.ok(result.outerHTML.indexOf('<label for="heyo">label</label>') !== -1, 'contains for="heyo"')
  t.end()
})

test('allow objects to be passed', function (t) {
  t.plan(1)
  var result = bel`<div>
    <div ${{ foo: 'bar' }}>hey</div>
  </div>`
  t.ok(result.outerHTML.indexOf('<div foo="bar">hey</div>') !== -1, 'contains foo="bar"')
  t.end()
})
