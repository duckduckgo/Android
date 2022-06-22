import { overrideProperty } from '../utils'

export function init (args) {
    const Navigator = globalThis.Navigator
    const navigator = globalThis.navigator

    overrideProperty('keyboard', {
        object: Navigator.prototype,
        origValue: navigator.keyboard,
        targetValue: undefined
    })
    overrideProperty('hardwareConcurrency', {
        object: Navigator.prototype,
        origValue: navigator.hardwareConcurrency,
        targetValue: 2
    })
    overrideProperty('deviceMemory', {
        object: Navigator.prototype,
        origValue: navigator.deviceMemory,
        targetValue: 8
    })
}
