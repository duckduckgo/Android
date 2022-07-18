let test = require('tape')
let bel = require('../')
/* Note:
Failing tests have been commented. They include the following:
  onfocusin
  onfocusout
  ontouchcancel
  ontouchend
  ontouchmove
  ontouchstart
  onunload
*/

function raiseEvent (element, eventName) {
  let event = document.createEvent('Event')
  event.initEvent(eventName, true, true)
  element.dispatchEvent(event)
}

test('should have onabort events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input onabort=${pass}></input>`

  raiseEvent(res, 'abort')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
})
test('should have onblur events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input onblur=${pass}></input>`

  raiseEvent(res, 'blur')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
})
test('should have onchange events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input onchange=${pass}></input>`

  raiseEvent(res, 'change')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
})
test('should have onclick events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input onclick=${pass}></input>`

  raiseEvent(res, 'click')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
})
test('should have oncontextmenu events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input oncontextmenu=${pass}></input>`

  raiseEvent(res, 'contextmenu')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
})
test('should have ondblclick events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input ondblclick=${pass}></input>`

  raiseEvent(res, 'dblclick')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
})
test('should have ondrag events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input ondrag=${pass}></input>`

  raiseEvent(res, 'drag')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
})
test('should have ondragend events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input ondragend=${pass}></input>`

  raiseEvent(res, 'dragend')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
})
test('should have ondragenter events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input ondragenter=${pass}></input>`

  raiseEvent(res, 'dragenter')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
})
test('should have ondragleave events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input ondragleave=${pass}></input>`

  raiseEvent(res, 'dragleave')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
})
test('should have ondragover events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input ondragover=${pass}></input>`

  raiseEvent(res, 'dragover')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
})
test('should have ondragstart events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input ondragstart=${pass}></input>`

  raiseEvent(res, 'dragstart')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
})
test('should have ondrop events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input ondrop=${pass}></input>`

  raiseEvent(res, 'drop')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
})
test('should have onerror events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input onerror=${pass}></input>`

  raiseEvent(res, 'error')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
})
test('should have onfocus events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input onfocus=${pass}></input>`

  raiseEvent(res, 'focus')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
})
/* test('should have onfocusin events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input onfocusin=${pass}></input>`

  raiseEvent(res, 'focusin')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
}) */
/* test('should have onfocusout events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input onfocusout=${pass}></input>`

  raiseEvent(res, 'focusout')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
}) */
test('should have oninput events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input oninput=${pass}></input>`

  raiseEvent(res, 'input')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
})
test('should have onkeydown events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input onkeydown=${pass}></input>`

  raiseEvent(res, 'keydown')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
})
test('should have onkeypress events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input onkeypress=${pass}></input>`

  raiseEvent(res, 'keypress')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
})
test('should have onkeyup events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input onkeyup=${pass}></input>`

  raiseEvent(res, 'keyup')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
})
test('should have onmousedown events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input onmousedown=${pass}></input>`

  raiseEvent(res, 'mousedown')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
})
test('should have onmouseenter events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input onmouseenter=${pass}></input>`

  raiseEvent(res, 'mouseenter')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
})
test('should have onmouseleave events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input onmouseleave=${pass}></input>`

  raiseEvent(res, 'mouseleave')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
})
test('should have onmousemove events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input onmousemove=${pass}></input>`

  raiseEvent(res, 'mousemove')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
})
test('should have onmouseout events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input onmouseout=${pass}></input>`

  raiseEvent(res, 'mouseout')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
})
test('should have onmouseover events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input onmouseover=${pass}></input>`

  raiseEvent(res, 'mouseover')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
})
test('should have onmouseup events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input onmouseup=${pass}></input>`

  raiseEvent(res, 'mouseup')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
})
test('should have onreset events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input onreset=${pass}></input>`

  raiseEvent(res, 'reset')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
})
test('should have onresize events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input onresize=${pass}></input>`

  raiseEvent(res, 'resize')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
})
test('should have onscroll events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input onscroll=${pass}></input>`

  raiseEvent(res, 'scroll')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
})
test('should have onselect events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input onselect=${pass}></input>`

  raiseEvent(res, 'select')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
})
test('should have onsubmit events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input onsubmit=${pass}></input>`

  raiseEvent(res, 'submit')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
})
/* test('should have ontouchcancel events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input ontouchcancel=${pass}></input>`

  raiseEvent(res, 'touchcancel')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
}) */
/* test('should have ontouchend events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input ontouchend=${pass}></input>`

  raiseEvent(res, 'touchend')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
}) */
/* test('should have ontouchmove events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input ontouchmove=${pass}></input>`

  raiseEvent(res, 'touchmove')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
}) */
/* test('should have ontouchstart events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<input ontouchstart=${pass}></input>`

  raiseEvent(res, 'touchstart')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
}) */
/* test('should have onunload events(html attribute) ', function (t) {
  t.plan(1)
  let expectationMet = false
  let res = bel`<body onunload=${pass}></body>`

  raiseEvent(res, 'unload')

  function pass (e) {
    e.preventDefault()
    expectationMet = true
  }

  t.equal(expectationMet, true, 'result was expected')
}) */
