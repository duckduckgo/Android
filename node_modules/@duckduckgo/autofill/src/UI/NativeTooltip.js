import {getInputType, getMainTypeFromType, getSubtypeFromType} from '../Form/matching'

/**
 * A 'Native' tooltip means that that autofill is not responsible
 * for rendering **any** UI relating to the selecting of items
 *
 * @implements {TooltipInterface}
 */
export class NativeTooltip {
    /**
     * To 'attach' on iOS/Android is to ask the runtime for autofill data - this
     * will eventually cause the native overlays to show
     * @param args
     */
    attach (args) {
        const {form, input, device} = args
        const inputType = getInputType(input)
        const mainType = getMainTypeFromType(inputType)

        const subType = getSubtypeFromType(inputType)

        if (mainType === 'unknown') {
            throw new Error('unreachable, should not be here if (mainType === "unknown")')
        }

        /** @type {GetAutofillDataRequest} */
        const payload = {
            inputType,
            mainType,
            subType
        }
        device.getAutofillData(payload)
            .then(resp => {
                console.log('Autofilling...', resp, mainType)
                form.autofillData(resp, mainType)
            })
            .catch(e => {
                console.error('NativeTooltip::device.getAutofillData(payload)')
                console.error(e)
            })
    }
}
