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
        getDeviceCapabilities(): string
        removeCredentials()
    }

    // Used in the Android app
    BrowserAutofill: {
        getAutofillData(data: string): void;
        storeFormData(data: string): void;
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

    /**
     * This adds type support for the custom message that native sides may
     * send to indicate where a mouseMove event occurred
     */
    addEventListener(type: "mouseMove", listener: (this: Document, ev: CustomEvent<{x: number, y: number}>) => void): void;
}
