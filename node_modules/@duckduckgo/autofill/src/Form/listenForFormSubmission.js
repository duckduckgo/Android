/**
 * @param {Map<HTMLElement, import("./Form").Form>} forms
 */
const listenForGlobalFormSubmission = (forms) => {
    try {
        window.addEventListener('submit', (e) =>
            // @ts-ignore
            forms.get(e.target)?.submitHandler(),
        true)

        window.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                const focusedForm = [...forms.values()].find((form) => form.hasFocus(e))
                focusedForm?.submitHandler()
            }
        })

        const observer = new PerformanceObserver((list) => {
            const entries = list.getEntries().filter((entry) =>
                // @ts-ignore why does TS not know about `entry.initiatorType`?
                ['fetch', 'xmlhttprequest'].includes(entry.initiatorType) &&
                /login|sign-in|signin/.test(entry.name)
            )

            if (!entries.length) return

            const filledForm = [...forms.values()].find(form => form.hasValues())
            filledForm?.submitHandler()
        })
        observer.observe({entryTypes: ['resource']})
    } catch (error) {
        // Unable to detect form submissions using AJAX calls
    }
}

export default listenForGlobalFormSubmission
