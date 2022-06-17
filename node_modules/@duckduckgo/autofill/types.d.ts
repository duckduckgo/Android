// These are new additions to the API, only available in Chrome: https://web.dev/intersectionobserver-v2/.
interface IntersectionObserverEntry {
    isVisible?: boolean
}

interface IntersectionObserverInit {
    trackVisibility?: boolean
    delay?: number
}

interface Window {
    // Used in the Android app
    EmailInterface: {
        isSignedIn(): string
        getUserData(): string
        storeCredentials(token: string, username: string, cohort: string): string
        showTooltip()
    }


    // Used in the Android app
    BrowserAutofill: {
        getRuntimeConfiguration(): string;
        getAvailableInputTypes(): string;
        getAutofillData(data: string): void;
        storeFormData(data: string): void;
    }

    chrome: {
        webview: {
            postMessage: Window['postMessage'],
            addEventListener: Window['addEventListener'],
            removeEventListener: Window['removeEventListener'],
        }
    }

    // Used in Apple apps
    webkit: {
        messageHandlers: Record<string, {
            postMessage(data: any): Promise<any>
        }>
    }

    __playwright: {
        mocks: {
            calls: MockCall[]
        }
    }
}
