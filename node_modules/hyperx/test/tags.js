var test = require('tape')
var vdom = require('virtual-dom')
var hyperx = require('../')
var hx = hyperx(vdom.h)

test('tag as string variable', function (t) {
  var tag = 'div'
  var tree = hx`<${tag} class="wow"></${tag}>`
  t.equal(vdom.create(tree).toString(), '<div class="wow"></div>')
  t.end()
})

test('tag as function variable', function (t) {
  var customTag = function () {}
  var h = function (tag, attrs, children) {
    t.equal(tag, customTag)
    t.deepEqual(attrs, { className: 'wow' })
    t.deepEqual(children, [ 'child' ])
    t.end()
  }
  var hx = hyperx(h)
  var tree = hx`<${customTag} class="wow">child</${customTag}>`
})
