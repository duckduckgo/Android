import { formatDuckAddress, escapeXML } from '../autofill-utils'
import HTMLTooltip from './HTMLTooltip'

class EmailHTMLTooltip extends HTMLTooltip {
    /**
     * @param {import("../DeviceInterface/InterfacePrototype").default} device
     */
    render (device) {
        this.device = device
        this.addresses = device.getLocalAddresses()

        this.shadow.innerHTML = `
${this.options.css}
<div class="wrapper wrapper--email">
    <div class="tooltip tooltip--email" hidden>
        <button class="tooltip__button tooltip__button--email js-use-personal">
            <span class="tooltip__button--email__primary-text">
                Use <span class="js-address">${formatDuckAddress(escapeXML(this.addresses.personalAddress))}</span>
            </span>
            <span class="tooltip__button--email__secondary-text">Blocks email trackers</span>
        </button>
        <button class="tooltip__button tooltip__button--email js-use-private">
            <span class="tooltip__button--email__primary-text">Use a Private Address</span>
            <span class="tooltip__button--email__secondary-text">Blocks email trackers and hides your address</span>
        </button>
    </div>
</div>`
        this.wrapper = this.shadow.querySelector('.wrapper')
        this.tooltip = this.shadow.querySelector('.tooltip')
        this.usePersonalButton = this.shadow.querySelector('.js-use-personal')
        this.usePrivateButton = this.shadow.querySelector('.js-use-private')
        this.addressEl = this.shadow.querySelector('.js-address')

        this.updateAddresses = (addresses) => {
            if (addresses && this.addressEl) {
                this.addresses = addresses
                this.addressEl.textContent = formatDuckAddress(addresses.personalAddress)
            }
        }
        this.registerClickableButton(this.usePersonalButton, () => {
            this.fillForm('personalAddress')
        })
        this.registerClickableButton(this.usePrivateButton, () => {
            this.fillForm('privateAddress')
        })

        // Get the alias from the extension
        this.device.getAddresses().then(this.updateAddresses)

        this.init()
        return this
    }
    /**
     * @param {'personalAddress' | 'privateAddress'} id
     */
    async fillForm (id) {
        const address = this.addresses[id]
        const formattedAddress = formatDuckAddress(address)
        this.device?.selectedDetail({email: formattedAddress, id}, 'email')
    }
}

export default EmailHTMLTooltip
