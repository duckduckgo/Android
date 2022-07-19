const mixins = require('./mixins/index.es6.js')
const store = require('./store.es6.js')

function BasePage (ops) {
    this.views = {}
    this.store = store
    this.ready()
}

BasePage.prototype = window.$.extend({},
    mixins.events,
    {

        // pageName: '' - should be unique, defined by each page subclass

        ready: function () {}

    }
)

module.exports = BasePage
