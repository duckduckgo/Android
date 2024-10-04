/**
 * The user wishes to enable DuckPlayer
 * @satisfies {import("./messages").UserValues}
 */
const enabled = {
    privatePlayerMode: { enabled: {} },
    overlayInteracted: false
}

console.log(enabled)

/**
 * The user wishes to disable DuckPlayer
 * @satisfies {import("./messages").UserValues}
 */
const disabled = {
    privatePlayerMode: { disabled: {} },
    overlayInteracted: false
}

console.log(disabled)

/**
 * The user wishes for overlays to always show
 * @satisfies {import("./messages").UserValues}
 */
const alwaysAsk = {
    privatePlayerMode: { alwaysAsk: {} },
    overlayInteracted: false
}

console.log(alwaysAsk)

/**
 * The user wishes only for small overlays to show, not the blocking video ones
 * @satisfies {import("./messages").UserValues}
 */
const alwaysAskRemembered = {
    privatePlayerMode: { alwaysAsk: {} },
    overlayInteracted: true
}

console.log(alwaysAskRemembered)
