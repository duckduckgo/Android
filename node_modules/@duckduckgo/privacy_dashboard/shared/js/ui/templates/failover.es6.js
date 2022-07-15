const bel = require('bel')

module.exports = function () {
    return bel`<section class="failover card">
    <p>${this.message}</p>
    </section>`
}
