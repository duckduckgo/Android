const bel = require('bel')

module.exports = function (items, extraClasses) {
    extraClasses = extraClasses || ''

    return bel`<ul class="status-list ${extraClasses}">
    ${items.map(renderItem)}
</ul>`
}

function renderItem (item) {
    return bel`<li class="status-list__item status-list__item--${item.modifier}
    bold ${item.highlight ? 'is-highlighted' : ''}">
    ${item.msg}
</li>`
}
