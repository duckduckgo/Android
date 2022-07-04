import {attachAndReturnGenericForm} from '../../test-utils'
import {Form} from '../../Form/Form'
import InterfacePrototype from '../InterfacePrototype'

describe('InterfacePrototype', function () {
    beforeEach(() => {
        require('../../requestIdleCallback')
    })

    /**
     * On macOS we load some tabs in the background at startup. If one of those tabs called focus() on the fields
     * we may have flashed the tooltip. This test ensure we don't regress there.
     * @asana https://app.asana.com/0/1200930669568058/1202411700616532/f
     */
    it('will not fire attach if the document is hidden', () => {
        const mockedDoc = jest.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden')

        const device = InterfacePrototype.default()
        device.init()
        const formEl = attachAndReturnGenericForm()
        const input = /** @type {HTMLInputElement} */ (formEl.querySelector('input'))
        const formInstance = new Form(formEl, input, device)
        const spy = jest.spyOn(device.uiController, 'attach')
        device.attachTooltip(formInstance, input, null)
        expect(spy).not.toHaveBeenCalled()

        mockedDoc.mockRestore()
    })
})
