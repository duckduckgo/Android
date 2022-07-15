const Parent = window.DDG.base.View

function UserData (ops) {
    this.model = ops.model
    this.pageView = ops.pageView
    this.template = ops.template

    Parent.call(this, ops)

    // bind events
    this.setup()
}

UserData.prototype = window.$.extend({},
    Parent.prototype,
    {

        _logout: function (e) {
            e.preventDefault()
            this.model.logout()
        },

        setup: function () {
            this._cacheElems('.js-userdata', ['logout'])

            this.bindEvents([
                [this.$logout, 'click', this._logout],
                // listen for changes to the userData model
                [this.store.subscribe, 'change:userData', this.rerender]
            ])
        },

        rerender: function () {
            this.unbindEvents()
            this._rerender()
            this.setup()
        }
    }
)

module.exports = UserData
