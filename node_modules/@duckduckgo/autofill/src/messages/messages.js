import {Message} from './message'
import validators from '../schema/validators.cjs'
import getRuntimeConfiguration from '../schema/response.getRuntimeConfiguration.schema.json'
import getAvailableInputTypes from '../schema/response.getAvailableInputTypes.schema.json'
import getAutofillData from '../schema/response.getAutofillData.schema.json'

/**
 * This file contains every message this application can 'send' to
 * a native side.
 */

/**
 * @extends {Message<undefined, AvailableInputTypes>}
 */
export class GetAvailableInputTypes extends Message {
    name = 'getAvailableInputTypes'
    responseName = getAvailableInputTypes.properties.type.const;
    resValidator = validators['#/definitions/GetAvailableInputTypesResponse']
}

/**
 * @extends {Message}
 */
export class CloseAutofillParent extends Message {
    name = 'closeAutofillParent'
}

/**
 * @extends {Message<GetAutofillCredentials, Credentials>}
 */
export class GetAutofillCredentialsMsg extends Message {
    name = 'getAutofillCredentials'
    reqValidator = validators['#/definitions/GetAutofillCredentials']
}

/**
 * @extends {Message<ShowAutofillParentRequest, void>}
 */
export class ShowAutofillParent extends Message {
    // @ts-ignore
    reqValidator = validators['#/definitions/ShowAutofillParentRequest']
    name = 'showAutofillParent'
}

/**
 * @extends {Message<null, any>}
 */
export class GetSelectedCredentials extends Message {
    name = 'getSelectedCredentials'
}

/**
 * @extends {Message<DataStorageObject, void>}
 */
export class StoreFormData extends Message {
    name = 'storeFormData'
    // @ts-ignore
    reqValidator = validators['#/definitions/StoreFormDataRequest']
}

/**
 * @typedef {StoreFormData} Names2
 */

/**
 * @extends {Message<null, InboundPMData>}
 */
export class GetAutofillInitData extends Message {
    name = 'getAutofillInitData'
    // @ts-ignore
    resValidator = validators['#/definitions/GetAutofillInitDataResponse']
}

/**
 * @extends {Message<{data: Record<string, any>, configType: string}, InboundPMData>}
 */
export class SelectedDetailMessage extends Message {
    name = 'selectedDetail'
}

/**
 * @extends {Message<GetAutofillDataRequest, IdentityObject|CredentialsObject|CreditCardObject>}
 */
export class GetAutofillData extends Message {
    name = 'getAutofillData'
    // @ts-ignore
    reqValidator = validators['#/definitions/GetAutofillDataRequest']
    // @ts-ignore
    resValidator = validators['#/definitions/GetAutofillDataResponse']
    responseName = getAutofillData.properties.type.const
    preResponseValidation (response) {
        const cloned = JSON.parse(JSON.stringify(response.success))
        if ('id' in cloned) {
            if (typeof cloned.id === 'number') {
                console.warn("updated the credentials' id field as it was a number, but should be a string")
                cloned.id = String(cloned.id)
            }
        }
        return {
            success: cloned
        }
    }
}

/**
 * @extends {Message<null, RuntimeConfiguration>}
 */
export class GetRuntimeConfiguration extends Message {
    name = 'getRuntimeConfiguration'
    // @ts-ignore
    resValidator = validators['#/definitions/GetRuntimeConfigurationResponse']
    responseName = getRuntimeConfiguration.properties.type.const
}

/**
 * @extends Message<undefined, {isAppSignedIn: boolean}>
 */
export class EmailSignedIn extends Message {
    name = 'emailHandlerCheckAppSignedInStatus'
}
/**
 * @extends Message<undefined, {isAppSignedIn: boolean}>
 */
export class EmailRefreshAlias extends Message {
    name = 'emailHandlerRefreshAlias'
    preResponseValidation (response) {
        return { success: response }
    }
}

/**
 * Use this to wrap legacy messages where schema validation is not available.
 */
export class LegacyMessage extends Message {}

/**
 * @template [Req=any]
 * @param {string} name
 * @param {Req} [data]
 * @returns {Message<Req, any>}
 */
export function createLegacyMessage (name, data) {
    const message = new LegacyMessage(data)
    message.name = name
    return message
}
