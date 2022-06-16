/**
 * Given some ruleSets, create an efficient
 * lookup system for accessing cached regexes by name.
 *
 * @param {VendorRegexConfiguration["rules"]} rules
 * @param {VendorRegexConfiguration["ruleSets"]} ruleSets
 * @return {{RULES: Record<keyof VendorRegexRules, RegExp | undefined>}}
 */
function createCacheableVendorRegexes (rules, ruleSets) {
    const vendorRegExp = {
        RULES: rules,
        RULE_SETS: ruleSets,
        _getRule (name) {
            let rules = []
            this.RULE_SETS.forEach(set => {
                if (set[name]) {
                    // Add the rule.
                    // We make the regex lower case so that we can match it against the
                    // lower-cased field name and get a rough equivalent of a case-insensitive
                    // match. This avoids a performance cliff with the "iu" flag on regular
                    // expressions.
                    rules.push(`(${set[name]?.toLowerCase()})`.normalize('NFKC'))
                }
            })
            const value = new RegExp(rules.join('|'), 'u')
            Object.defineProperty(this.RULES, name, {get: undefined})
            Object.defineProperty(this.RULES, name, {value})
            return value
        },
        init () {
            Object.keys(this.RULES).forEach(field =>
                Object.defineProperty(this.RULES, field, {
                    get () {
                        return vendorRegExp._getRule(field)
                    }
                })
            )
        }
    }
    vendorRegExp.init()
    // @ts-ignore
    return vendorRegExp
}

module.exports.createCacheableVendorRegexes = createCacheableVendorRegexes
