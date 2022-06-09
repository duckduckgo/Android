import {DeviceApiCall} from '../../packages/device-api'
import {getAliasParamsSchema, getAliasResultSchema} from './__generated__/validators.zod'

/**
 * @extends {DeviceApiCall<getAliasParamsSchema, getAliasResultSchema>}
 */
export class GetAlias extends DeviceApiCall {
    method = 'emailHandlerGetAlias'
    id = 'n/a'
    paramsValidator = getAliasParamsSchema
    resultValidator = getAliasResultSchema
    preResultValidation (response) {
        // convert to the correct format, because this is a legacy API
        return { success: response }
    }
}
