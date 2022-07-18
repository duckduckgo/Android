function rawCreateElement (tag) {
  if (typeof window !== 'undefined') {
    return browser()
  } else {
    return server()
  }

  function browser () {
    var el = document.createElement('div')
    el.innerHTML = tag
    return toArray(el.childNodes)
  }

  function server () {
    var wrapper = new String(tag) // eslint-disable-line no-new-wrappers
    wrapper.__encoded = true
    return wrapper
  }
}

function toArray (arr) {
  return Array.isArray(arr) ? arr : [].slice.call(arr)
}

module.exports = rawCreateElement
