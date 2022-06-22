type Platform = "ios" | "macos" | "android" | "extension" | "windows";
type Replacements = Record<keyof GlobalConfig, any>;
type MockCall = [name: string, input: any, output: any];

interface CredentialsMock {
    username: string,
    password: string,
    id: string
}

/**
 * This is an API Abstraction for mock user data.
 *
 * All implementations across all platforms must use this
 * to generate mock data. This will give us a consistent, platform-independent
 * way to describe user-state within tests that's easy to read/maintain
 *
 * ```js
 * await webkitMocks()
 *    .withPrivateEmail("abc")
 *    .withPersonalEmail("example")
 *    .applyTo(page)
 * ```
 */
interface MockBuilder<State, Mocks extends Record<string, any>> {
    // Set the private email address
    withPrivateEmail(email: string): MockBuilder<State, Mocks>
    // Set the personal email address
    withPersonalEmail(email: string): MockBuilder<State, Mocks>
    // Add an identity
    withIdentity(identity: IdentityObject): MockBuilder<State, Mocks>
    // Add a credential
    withCredentials(credentials: CredentialsMock): MockBuilder<State, Mocks>
    // Add available input types
    withAvailableInputTypes(inputTypes: import("../../src/deviceApiCalls/__generated__/validators-ts").AvailableInputTypes): MockBuilder<State, Mocks>
    // Add any number of feature toggle overrides
    withFeatureToggles(featureToggles: Partial<import("../../src/deviceApiCalls/__generated__/validators-ts").AutofillFeatureToggles>): MockBuilder<State, Mocks>
    // Remove handlers to test roll-out logic
    removeHandlers(handlers: (keyof Mocks)[]): MockBuilder<State, Mocks>
    // observe the current state
    tap(fn: (currentState: State) => void): MockBuilder<State, Mocks>
    // apply to the page, this is the final step
    applyTo(page: import("playwright").Page): Promise<void>
}

/**
 * Implement this when inserting JavaScript into pages.
 *
 * The <Omit, "x"> pattern enforces correct usage: eg: it prevents
 * you missing 'platform' and ensures applyTo is the very last method.
 *
 * ```js
 * await scriptBuilder().replace("a", "b").platform("macos").applyTo(page)
 * ```
 */
interface ScriptBuilder {
    // replace a single config key
    replace(key: keyof GlobalConfig, value: string | boolean): Omit<ScriptBuilder, "applyTo">
    // replace multiple config keys
    replaceAll(replacements: Partial<Replacements>): Omit<ScriptBuilder, "applyTo">
    // observe the current state
    tap(fn: (replacements: Partial<Replacements>, platform: string) => void): Omit<ScriptBuilder, "applyTo">
    // set the platform - this is required
    platform(platform: Platform): Omit<ScriptBuilder, "platform">
    // apply to the page, this is the final step
    applyTo(page: import("playwright").Page): Promise<void>
}

/**
 * Utilities for dealing with spawned servers
 */
interface ServerWrapper {
    address: import("net").AddressInfo;
    urlForPath(path: string): string;
    close(): void;
    url: URL;
}
