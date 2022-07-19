'use strict'

const Module = require('module')

var BOOL_PROPS = [
  'autofocus', 'checked', 'defaultchecked', 'disabled', 'formnovalidate',
  'indeterminate', 'readonly', 'required', 'selected', 'willvalidate'
]

var BOOL_PROP_PATTERN = new RegExp(' (' + BOOL_PROPS.join('|') + '|onclick)=(""|\'\')', 'ig')
var DISABLED_PATTERN = new RegExp('disabled=("true"|\'true\')', 'ig')

const replaceMap = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  '\'': '&#039;'
}
const replaceMapRE = new RegExp(Object.keys(replaceMap).join('|'), 'g')

function replaceMapper (matched){
  return replaceMap[matched]
}

function escape (value) {
  return value.toString().replace(replaceMapRE, replaceMapper)
}

function handleValue (value) {
  if (value === null || value === undefined || value === false) {
    return ''
  }

  if (Array.isArray(value)) {
    // Suppose that each item is a result of html``.
    return value.join('')
  }
  // Ignore event handlers.
  //     onclick=${(e) => doSomething(e)}
  // will become
  //     onclick=""
  const valueType = typeof value
  if (valueType === 'function') {
    return ''
  }

  if (valueType === 'object' && value.constructor.name !== 'String') {
    return objToString(value)
  }

  if (value.__encoded) {
    return value
  }

  return escape(value)
}

function stringify () {
  var pieces = arguments[0]
  var output = ''
  for (var i = 0; i < pieces.length - 1; i++) {
    var piece = pieces[i]
    var value = handleValue(arguments[i + 1])
    if (piece[piece.length - 1] === '=') {
      output += piece + '"' + value + '"'
    } else {
      output += piece + value
    }
  }
  output += pieces[i]
  output = output
    .replace(DISABLED_PATTERN, 'disabled="disabled"')
    .replace(BOOL_PROP_PATTERN, '')

  // HACK: Avoid double encoding by marking encoded string
  // You cannot add properties to string literals
  // eslint-disable-next-line no-new-wrappers
  const wrapper = new String(output)
  wrapper.__encoded = true
  return wrapper
}

function objToString (obj) {
  var values = ''
  const keys = Object.keys(obj)
  for (var i = 0; i < keys.length - 1; i++) {
    values += keys[i] + '="' + escape(obj[keys[i]] || '') + '" '
  }
  return values + keys[i] + '="' + escape(obj[keys[i]] || '') + '"'
}

function replace (moduleId) {
  const originalRequire = Module.prototype.require
  Module.prototype.require = function (id) {
    if (id === moduleId) {
      return stringify
    } else {
      return originalRequire.apply(this, arguments)
    }
  }
}

stringify.replace = replace

module.exports = stringify
