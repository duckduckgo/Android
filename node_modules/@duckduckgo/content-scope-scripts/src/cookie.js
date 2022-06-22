export class Cookie {
    constructor (cookieString) {
        this.parts = cookieString.split(';')
        this.parse()
    }

    parse () {
        const EXTRACT_ATTRIBUTES = new Set(['max-age', 'expires', 'domain'])
        this.attrIdx = {}
        this.parts.forEach((part, index) => {
            const kv = part.split('=', 1)
            const attribute = kv[0].trim()
            const value = part.slice(kv[0].length + 1)
            if (index === 0) {
                this.name = attribute
                this.value = value
            } else if (EXTRACT_ATTRIBUTES.has(attribute.toLowerCase())) {
                this[attribute.toLowerCase()] = value
                this.attrIdx[attribute.toLowerCase()] = index
            }
        })
    }

    getExpiry () {
        if (!this.maxAge && !this.expires) {
            return NaN
        }
        const expiry = this.maxAge
            ? parseInt(this.maxAge)
            : (new Date(this.expires) - new Date()) / 1000
        return expiry
    }

    get maxAge () {
        return this['max-age']
    }

    set maxAge (value) {
        if (this.attrIdx['max-age'] > 0) {
            this.parts.splice(this.attrIdx['max-age'], 1, `max-age=${value}`)
        } else {
            this.parts.push(`max-age=${value}`)
        }
        this.parse()
    }

    toString () {
        return this.parts.join(';')
    }
}
