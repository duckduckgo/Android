// create global $
window.$ = window.jQuery = require('jquery')

// base dependencies

const i18next = require('./localize.es6.js')
const mixins = require('./mixins/index.es6.js')
const BaseModel = require('./model.es6.js')
const BasePage = require('./page.es6.js')
const BaseView = require('./view.es6.js')

// init base
window.DDG = window.DDG || {}
window.DDG.base = {
    mixins: mixins,
    Model: BaseModel,
    Page: BasePage,
    utils: {},
    View: BaseView,
    i18n: i18next
}

require('../pages/popup.es6.js')
