const $ = require('jquery')
const mixins = require('./mixins/index.es6.js')
const store = require('./store.es6.js')

/**
 * Abstract Base class for any type of view.
 *
 * Contains the basic functionality
 * for rendering a template, caching a $ ref, inserting
 * into the DOM and a locally scoped find at this.$
 *
 * @constructor
 * @param {object} ops
 */

function BaseView (ops) {
    this.model = ops.model
    this.views = this.views || {}
    this.store = store

    // A jquery object should be passed in as either 'appendTo', 'before' or 'after'
    // indicating where on the DOM the view should be added. If none is passed
    // the view will render itself to an in-memory jquery object, but won't be added to the DOM.
    this.$parent = (typeof ops.appendTo === 'string') ? $(ops.appendTo) : ops.appendTo
    this.$before = (typeof ops.before === 'string') ? $(ops.before) : ops.before
    this.$after = (typeof ops.after === 'string') ? $(ops.after) : ops.after

    if (ops.events) {
        for (const id in ops.events) {
            this.on(id, ops.events[id])
        }
    }
    this._render(ops)
}

BaseView.prototype = $.extend(
    {},
    mixins.events,
    {
        /***
         * Each view should define a template
         * if it wants to be rendered and added to the DOM.
         *
         * template: '',
         */

        /**
         * Removes the view element (and all child view elements)
         * from the DOM.
         *
         * Should be extended to do any cleanup of child views or
         * unbinding of events.
         */
        destroy: function () {
            this.unbindEvents()
            this.destroyChildViews()
            this.$el.remove()
            if (this.model) this.model.destroy()
        },

        /**
         * Go through the this.views object
         * and recurse down destroying any child
         * views and their child views so that
         * when a view is destroyed it removes all memory
         * footprint, all events are cleanly unbound and
         * all related DOM elements are removed.
         *
         */
        destroyChildViews: function () {
            !function destroyViews (views) { // eslint-disable-line
                if (!views) { return }
                let v
                if ($.isArray(views)) {
                    for (let i = 0; i < views.length; i++) {
                        v = views[i]
                        if (v && $.isArray(v)) {
                            destroyViews(v)
                        } else {
                            v && v.destroy && v.destroy()
                        }
                    }
                    views = null
                } else {
                    for (const c in views) {
                        v = views[c]
                        if (v && $.isArray(v)) {
                            destroyViews(v)
                        } else {
                            v && v.destroy && v.destroy()
                        }
                        delete views[c]
                    }
                }
            }(this.views) // eslint-disable-line
            delete this.views
        },

        /**
         * Take the template defined on the view class and
         * use it to create a DOM element + append it to the DOM.
         *
         * Can be extended with any custom rendering logic
         * a view may need to do.
         *
         * @param {object} ops - the same ops hash passed into the view constructor
         */
        _render: function (ops) {
            if (!this.$el) {
                if (ops && ops.$el) {
                    this.$el = ops.$el
                } else {
                    const el = this.template()
                    this.$el = $(el)
                }
            }

            if (!this.$el) throw new Error('Template Not Found: ' + this.template)
            this._addToDOM()
            this.$ = this.$el.find.bind(this.$el)
        },

        _rerender: function () {
            const $prev = this.$el.prev()
            if ($prev.length) {
                delete this.$parent
                this.$after = $prev
            } else {
                const $next = this.$el.next()
                if ($next.length) {
                    delete this.$parent
                    this.$before = $next
                }
            }

            this.$el.remove()
            delete this.$el
            this._render()
        },

        /**
         * Add the rendered element to the DOM.
         */
        _addToDOM: function () {
            if (this.$parent) {
                this.$parent.append(this.$el)
            } else if (this.$before) {
                this.$before.before(this.$el)
            } else if (this.$after) {
                this.$after.after(this.$el)
            }
        },

        /**
         * Takes a prefix string and an array
         * of elements and caches dom references.
         *
         * It should be used like this:
         *
         * this._cacheElems('.js-detail',['next','prev'])
         * --> this.$next (is cached ref to '.js-detail-next'
         *   this.$prev (is cached ref to '.js-detail-prev'
         *
         * @param {String} prefix
         * @param {Array} elems
         */
        _cacheElems: function (prefix, elems) {
            for (let i = 0; i < elems.length; i++) {
                const selector = prefix + '-' + elems[i]
                // the replace removes '-' from class names, so:
                // 'class-name' becomes this.$classname:
                const id = '$' + elems[i].replace(/-/g, '')
                this[id] = this.$(selector)
            }
        }
    }
)

module.exports = BaseView
