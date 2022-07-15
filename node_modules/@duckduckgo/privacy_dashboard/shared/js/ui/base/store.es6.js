/**
 * Base application `store`.
 *
 * This is how models communicate with other models, views and pages.
 * It is based on Redux pattern but slimmed down for our purposes.
 * It emits event notifications that can be subscribed to.
 *
 * Its purpose is to be the single source of truth for app-wide event
 * notifications (to avoid race conditions and spaghetti event tangles
 * that can result in the infinite loops and zombie states that early
 * Backbone and Angular 1.0 apps are famous for when components tried
 * to talk to one other without a single source of truth like this store).
 *
 * --> What you need to know as a feature developer: <--
 *
 *   - To PUBLISH a notification from a model:
 *
 *     `model.set('bar', 1234)`
 *    or
 *     `model.send('somethingClicked')`
 *
 *   - To SUBSCRIBE to notifications about a model's published state change:
 *
 *      `this.bindEvents([
 *        [this.store.subscribe, 'change:search', this._handler],
 *        [this.store.subscribe, 'action:search', this._clickHandler]
 *      ])`
 *
 *    The first argument passed to `this._handler()` will be an
 *    object containing details about the search model's state change.
 *    The first argument passed to `this._clickHandler()` will be
 *    be the action name, second argument will be optional jQuery event.
 *
 *
 * TODO: create a state injector for test mocks
 */

// Dependencies
const { isPlainObject } = require('is-plain-object')
const deepFreeze = require('deep-freeze')
const EventEmitter2 = require('eventemitter2')
const notifiers = require('./notifiers.es6.js')

/**
 * .register() creates a notifier function for each caller.
 * (models should be the callers in most cases).
 * @param {string} notifierName - unique name of registrant (i.e. model name)
 * @api public
 */
function register (notifierName) {
    if (typeof notifierName !== 'string') { throw new Error('notifierName argument must be a string') }
    if (notifiers.registered[notifierName]) { throw new Error(`notifierName argument must be unique to store ${notifierName} already exists`) }

    notifiers.add(notifierName)
    const combinedNotifiers = notifiers.combine()

    if (!_store) {
        _store = _createStore(combinedNotifiers)
        _store.subscribe((notification) => {
            notification = deepFreeze(notification) // make immutable before publishing
            _publish(notification) // publish notif. about state changes to subscribers
        })
    } else {
    // update reducers to include the newest registered here
        _store.replaceNotifier(combinedNotifiers)
    }
}

/**
 * .publish() dispatches a notification to the store which can be subscribed to.
 * Although this api method is public, most of what you need to do can be
 * done with model.set(), model.clear(), model.send() instead of directly here.
 * @api public
 */
function publish (notification) {
    _store.dispatch(notification)
}

/**
 * Broadcasts state change events out to subscribers
 * @api private, but exposed as `store.subscribe()` for clarity
 */
const _publisher = new EventEmitter2()
_publisher.setMaxListeners(100) // EventEmitter2 default of 10 is too low

/**
 * Emits notifications via _publisher
 * @api private
 */
function _publish (notification) {
    if (notification && notification.change) {
        _publisher.emit(`change:${notification.notifierName}`, notification)
    }
    if (notification && notification.action) {
        _publisher.emit(`action:${notification.notifierName}`, notification)
    }
}

/**
 * Remove notifier from store.
 * @param {string} notifierName
 * @api public
 */
function remove (notifierName) {
    if (notifiers.remove(notifierName)) {
        const combinedNotifiers = notifiers.combine()
        _store.replaceNotifier(combinedNotifiers)
    }
}

/**
 * `_store` is where notifiers live after they are registered.
 * Its api is not publicly exposed. Developers must use public api.
 * @api private
 */
let _store = null

/**
 * Create the store of notifiers and their notification dispatch functions.
 * This basically mimics a Redux store init pattern
 * and is liberally borrowed from Minidux
 * but slimmed down for our needs:
 * https://www.npmjs.com/package/minidux#var-store--createstorereducer-initialstate-enhancer
 * @api private
 */
function _createStore (notifier) {
    if (!notifier || typeof notifier !== 'function') throw new Error('notifier must be a function')

    let state = {}
    let listener = null
    let isEmitting = false

    function dispatch (notification) {
        if (!notification || !isPlainObject(notification)) throw new Error('notification parameter is required and must be a plain object')
        if (!notification.notifierName || typeof notification.notifierName !== 'string') throw new Error('notifierName property of notification parameter is required and must be a string')
        if (isEmitting) throw new Error('subscribers may not generate notifications')

        isEmitting = true
        state = notifier(state, notification)
        if (listener) listener(notification)
        isEmitting = false
        return notification
    }

    function subscribe (cb) {
        if (!cb || typeof cb !== 'function') throw new Error('listener must be a function')
        listener = cb
    }

    function replaceNotifier (next) {
        if (typeof next !== 'function') throw new Error('new notifier must be a function')
        notifier = next
    }

    dispatch({ notifierName: '@@createStore/INIT' })

    return {
        dispatch: dispatch,
        subscribe: subscribe,
        replaceNotifier: replaceNotifier
    }
}

// Public api
module.exports = {
    register: register, // registers a new notifier to the store (likely a model)
    publish: publish, // publish a notification from notifier to subscribers
    subscribe: _publisher, // subscribe to notifiers' notifications
    remove: remove // remove a notifier from the store
}
