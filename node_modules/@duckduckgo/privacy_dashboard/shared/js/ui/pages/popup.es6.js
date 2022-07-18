const Parent = window.DDG.base.Page
const SiteView = require('./../views/site.es6.js')
const SiteModel = require('./../models/site.es6.js')
const BackgroundMessageModel = require('./../models/background-message.es6.js')
const siteTemplate = require('./../templates/site.es6.js')

function Trackers (ops) {
    this.$parent = window.$('#popup-container')
    Parent.call(this, ops)
}

Trackers.prototype = window.$.extend({},
    Parent.prototype,
    {

        pageName: 'popup',

        ready: function () {
            Parent.prototype.ready.call(this)
            this.message = new BackgroundMessageModel()

            this.views.site = new SiteView({
                pageView: this,
                model: new SiteModel(),
                appendTo: window.$('#site-info-container'),
                template: siteTemplate
            })
        }
    }
)

// kickoff!
window.DDG = window.DDG || {}
window.DDG.page = new Trackers()
