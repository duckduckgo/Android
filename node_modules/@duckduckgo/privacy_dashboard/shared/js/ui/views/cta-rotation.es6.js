const Parent = window.DDG.base.View

function CtaRotationView (ops) {
    this.model = ops.model
    this.pageView = ops.pageView
    this.template = ops.template
    Parent.call(this, ops)
}

CtaRotationView.prototype = window.$.extend({},
    Parent.prototype,
    {}
)

module.exports = CtaRotationView
