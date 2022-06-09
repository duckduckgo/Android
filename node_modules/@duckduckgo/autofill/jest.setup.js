Object.assign(global, require('jest-chrome'))
// Mocks chrome API calls needed for autofill to run successfully
// @ts-ignore
chrome.runtime.sendMessage.mockImplementation(
    (message, callback) => {
        let response = {}
        if (message.getAddresses) {
            response = {
                privateAddress: '123test321',
                personalAddress: 'test'
            }
        }
        callback(response)
    }
)

// The autofill script bails if context is insecure, this enables tests to run
global.isSecureContext = true

const crypto = require('crypto')

Object.defineProperty(global.self, 'crypto', {
    value: {
        ...global.self.crypto,
        // @ts-ignore TS doesn't know of `crypto.webcrypto.subtle`
        subtle: crypto.webcrypto.subtle,
        getRandomValues: arr => crypto.randomFillSync(arr)
    }
})

/**
 * Utility function that mocks the `IntersectionObserver` API. Necessary for components that rely
 * on it, otherwise the tests will crash. Recommended to execute inside `beforeEach`.
 * @param intersectionObserverMock - Parameter that is sent to the `Object.defineProperty`
 * overwrite method. `jest.fn()` mock functions can be passed here if the goal is to not only
 * mock the intersection observer, but its methods.
 * @source https://javascript.tutorialink.com/js-testing-code-that-uses-an-intersectionobserver/
 */
function setupIntersectionObserverMock ({
    root = null,
    rootMargin = '',
    thresholds = [],
    disconnect = () => null,
    observe = () => null,
    takeRecords = () => [],
    unobserve = () => null
} = {}) {
    class MockIntersectionObserver {
        constructor () {
            this.root = root
            this.rootMargin = rootMargin
            this.thresholds = thresholds
            this.disconnect = disconnect
            this.observe = observe
            this.takeRecords = takeRecords
            this.unobserve = unobserve
        }
    }

    Object.defineProperty(window, 'IntersectionObserver', {
        writable: true,
        configurable: true,
        value: MockIntersectionObserver
    })

    Object.defineProperty(global, 'IntersectionObserver', {
        writable: true,
        configurable: true,
        value: MockIntersectionObserver
    })
}
setupIntersectionObserverMock()
