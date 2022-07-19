const Parent = window.DDG.base.View

function Failover (ops) {
    this.template = ops.template
    this.message = ops.message
    Parent.call(this, ops)
}

Failover.prototype = window.$.extend({},
    Parent.prototype,
    {

    }
)

module.exports = Failover
