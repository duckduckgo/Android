// @ts-check
const {isDeepStrictEqual} = require('util')

/**
 * ObjectMap extends Map, but determines key equality using Node.js’ `util.isDeepStrictEqual` rather than using [SameValueZero](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Map#key_equality). This makes using objects as keys a bit simpler.
 */
module.exports = class ObjectMap extends Map {
  #data

  constructor(iterable = []) {
    super()
    this.#data = iterable
  }

  clear() {
    this.#data = []
  }

  delete(key) {
    if (!this.has(key)) {
      return false
    }
    this.#data = this.#data.filter(([existingKey]) => !isDeepStrictEqual(existingKey, key))
    return true
  }

  entries() {
    return this.#data[Symbol.iterator]()
  }

  forEach(cb) {
    for (const [key, value] of this.#data) {
      cb(value, key, this.#data)
    }
  }

  get(key) {
    return this.#data.find(([existingKey]) => isDeepStrictEqual(existingKey, key))?.[1]
  }

  has(key) {
    return this.#data.findIndex(([existingKey]) => isDeepStrictEqual(existingKey, key)) !== -1
  }

  keys() {
    return this.#data.map(([key]) => key)[Symbol.iterator]()
  }

  set(key, value) {
    this.delete(key)
    this.#data.push([key, value])
    return this
  }

  values() {
    return this.#data.map(([, value]) => value)[Symbol.iterator]()
  }
}
