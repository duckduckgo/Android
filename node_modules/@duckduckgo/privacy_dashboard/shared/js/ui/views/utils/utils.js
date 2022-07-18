import { MDCRipple } from '@material/ripple'

function setupMaterialDesignRipple (...selectors) {
    selectors.forEach((selector) => {
        const $matches = document.querySelectorAll(selector)
        $matches.forEach(($el) => {
            $el.classList.add('material-design-ripple')
            MDCRipple.attachTo($el)
        })
    })
}

module.exports = {
    setupMaterialDesignRipple
}
