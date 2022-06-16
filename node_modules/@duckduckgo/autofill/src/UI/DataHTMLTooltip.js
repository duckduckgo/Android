import { escapeXML } from '../autofill-utils'
import HTMLTooltip from './HTMLTooltip'

class DataHTMLTooltip extends HTMLTooltip {
    /**
     * @param {InputTypeConfigs} config
     * @param {TooltipItemRenderer[]} items
     * @param {{onSelect(id:string): void}} callbacks
     */
    render (config, items, callbacks) {
        let hasAddedSeparator = false
        // Only show an hr above the first duck address button, but it can be either personal or private
        const shouldShowSeparator = (dataId) => {
            const shouldShow = ['personalAddress', 'privateAddress'].includes(dataId) && !hasAddedSeparator
            if (shouldShow) hasAddedSeparator = true
            return shouldShow
        }

        const topClass = this.options.wrapperClass || ''

        this.shadow.innerHTML = `
${this.options.css}
<div class="wrapper wrapper--data ${topClass}">
    <div class="tooltip tooltip--data" hidden>
        ${items.map((item) => {
        // these 2 are optional
        const labelSmall = item.labelSmall?.(this.subtype)
        const label = item.label?.(this.subtype)

        return `
            ${shouldShowSeparator(item.id()) ? '<hr />' : ''}
            <button id="${item.id()}" class="tooltip__button tooltip__button--data tooltip__button--data--${config.type} js-autofill-button" >
                <span class="tooltip__button__text-container">
                    <span class="label label--medium">${escapeXML(item.labelMedium(this.subtype))}</span>
                    ${label ? `<span class="label">${escapeXML(label)}</span>` : ''}
                    ${labelSmall ? `<span class="label label--small">${escapeXML(labelSmall)}</span>` : ''}
                </span>
            </button>
        `
    }).join('')}
    </div>
</div>`
        this.wrapper = this.shadow.querySelector('.wrapper')
        this.tooltip = this.shadow.querySelector('.tooltip')
        this.autofillButtons = this.shadow.querySelectorAll('.js-autofill-button')

        this.autofillButtons.forEach((btn) => {
            this.registerClickableButton(btn, () => {
                callbacks.onSelect(btn.id)
            })
        })

        this.init()
        return this
    }
}

export default DataHTMLTooltip
