function injectContentScript (src) {
    const elem = document.head || document.documentElement
    const script = document.createElement('script')
    script.src = src
    script.onload = function () {
        this.remove()
    }
    elem.appendChild(script)
}

injectContentScript(chrome.runtime.getURL('/autofill.js'))
