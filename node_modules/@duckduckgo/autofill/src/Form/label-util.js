const EXCLUDED_TAGS = ['SCRIPT', 'NOSCRIPT', 'OPTION', 'STYLE']

/**
 * Extract all strings of an element's children to an array.
 * "element.textContent" is a string which is merged of all children nodes,
 * which can cause issues with things like script tags etc.
 *
 * @param  {HTMLElement} element
 *         A DOM element to be extracted.
 * @returns {string[]}
 *          All strings in an element.
 */
const extractElementStrings = (element) => {
    const strings = []
    const _extractElementStrings = el => {
        if (EXCLUDED_TAGS.includes(el.tagName)) {
            return
        }

        // only take the string when it's an explicit text node
        if (el.nodeType === el.TEXT_NODE || !el.childNodes.length) {
            let trimmedText = el.textContent.trim()
            if (trimmedText) {
                strings.push(trimmedText)
            }
            return
        }

        for (let node of el.childNodes) {
            let nodeType = node.nodeType
            if (nodeType !== node.ELEMENT_NODE && nodeType !== node.TEXT_NODE) {
                continue
            }
            _extractElementStrings(node)
        }
    }
    _extractElementStrings(element)
    return strings
}

module.exports.extractElementStrings = extractElementStrings
