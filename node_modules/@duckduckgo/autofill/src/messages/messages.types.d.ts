type Names = keyof RuntimeMessages;

type RuntimeMessages = {
    getAvailableInputTypes: {
        response: Schema.GetAvailableInputTypesResponse,
        request: null
    },
    getRuntimeConfiguration: {
        response: { success: Record<string, any> }
        request: null
    },
    getAutofillData: {
        response: { success: CredentialsObject | CreditCardObject | IdentityObject },
        request: Schema.GetAutofillDataRequest
    },
    storeFormData: {
        request: DataStorageObject
        response: { success: {} },
    },
    showAutofillParent: {
        request: Schema.ShowAutofillParentRequest,
        response: { success: {} },
    },
    getSelectedCredentials: {
        request: null,
        response: { success: {} },
    },
    closeAutofillParent: {
        request: null,
        response: { success: {} },
    },
    getAutofillInitData: {
        request: null,
        response: { success: PMData },
    },
    getAutofillCredentials: {
        request: { id: string },
        response: { success: CredentialsObject },
    }
}

type Interceptions = {
    [N in Names]?: (config: GlobalConfig) => Promise<RuntimeMessages[N]['response']>
}

type Middlewares = {
    [N in Names]?: (response: RuntimeMessages[N]['response']) => Promise<RuntimeMessages[N]['response']>
}

type MocksObjectAndroid = {
    [N in Names]?: () => void
}

type MocksObjectWebkit = {
    [N in Names]?: RuntimeMessages[N]['response'] | null
}

