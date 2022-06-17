import Ajv from 'ajv'
import standalone from 'ajv/dist/standalone/index.js'
import { formatArrayMembers, formatUnionMembers } from './codegen-utils.js'
import { printJs } from './codegen-printers.js'

/**
 * @typedef {{json: import('json-schema').JSONSchema7, relative: string}} Input
 * @typedef {{name: string, type: string[], required?: boolean, description?: string, title?: string}} Member
 * @typedef {{name: string, members: Member[], source: string, description?: string, title?: string}} Interface
 * @typedef {{interfaces: Interface[], input: Input}} Group
 */

/**
 * @typedef ObjectArgs
 * @property {import('json-schema').JSONSchema7} value;
 * @property {string} propName;
 * @property {string} ident;
 * @property {Input} input;
 * @property {string} parentName;
 * @property {string} topName;
 * @property {string[]} [localRefs]
 * @property {string} [identPrefix]
 */

/**
 * @typedef ParseArgs
 * @property {Input} input
 * @property {import('json-schema').JSONSchema7} json
 * @property {string} topName
 * @property {string} [knownId]
 * @property {string} [identPrefix]
 * @property {string[]} [localRefs]
 */

/**
 * @param {import('json-schema').JSONSchema7} value
 * @param {string} propName
 */
function processArray (value, propName) {
    /** @type {Member[]} */
    const members = []
    const arrayMembers = []
    if (typeof value.items === 'boolean') {
        /** noop */
    } else if (Array.isArray(value.items)) {
        /** noop */
    } else {
        if (value.items?.$ref) {
            const name = value.items?.$ref.slice(14)
            arrayMembers.push(name)
        } else {
            arrayMembers.push('any')
        }
        members.push({
            name: propName,
            type: [formatArrayMembers(arrayMembers)]
        })
    }
    return members
}

/**
 * @param {import("json-schema").JSONSchema7} value
 * @param {string | string[]} localRefs
 * @param {ObjectArgs} args
 * @param {string} propName
 * @param {string} identPrefix
 */
function processUnknown (value, localRefs, args, propName, identPrefix) {
    /** @type {Member[]} */
    const members = []
    if (!value.type) {
        if (value?.$ref) {
            let name = identName(value.$ref)
            if (localRefs?.includes(name)) {
                name = args.parentName + name
            }
            members.push({
                name: propName,
                type: [name]
            })
        } else {
            console.log('object property without type or ref', value)
        }
    }

    /**
     * @param {string} value
     * @returns {string}
     */
    function identName (value) {
        const name = value.slice(14)
        if (identPrefix) {
            return identPrefix + name
        }
        return name
    }

    return members
}

class Parser {
    /** @type {ParseArgs} */
    args

    /** @type {Interface[]} */
    interfaces = [];

    constructor (args) {
        this.args = args
    }

    parse () {
        this.processOne(this.args)
        return this.interfaces
    }

    /**
     * @param {ParseArgs} args
     * @returns {Interface[]}
     */
    processOne (args) {
        const { json, input, knownId, identPrefix, localRefs, topName } = args
        if (!json.$id && !knownId) {
            console.log('no json.$id or knownId', json)
            return this.interfaces
        }
        if (!knownId && !json.$id.startsWith('#/definitions/')) {
            console.log('cannot find name')
            return this.interfaces
        }
        const parentName = knownId || json.$id?.slice(14)
        if (!parentName) {
            throw new Error('unreachable name should exist')
        }
        /** @type {Member[]} */
        const members = []
        const hasProps = Boolean(json.properties)
        const isObject = json.type === 'object'
        if (hasProps) {
            members.push(...this.processProperties(json, input, identPrefix, localRefs, parentName, topName))
        } else {
            if (isObject) {
                if (json.additionalProperties) {
                    if (typeof json.additionalProperties === 'boolean') {
                        members.push({ name: '[index: string]', type: ['unknown'], required: true })
                    } else {
                        if (json.additionalProperties.$ref) {
                            // members.push()
                            let topName = json.additionalProperties.$ref?.slice(14)
                            if (identPrefix) {
                                topName = identPrefix + topName
                            }
                            members.push({ name: '[index: string]', type: [topName], required: true })
                        }
                    }
                }
            }
        }
        if (json.definitions) {
            for (const [defName, defValue] of Object.entries(json.definitions)) {
                const ident = parentName + defName
                if (typeof defValue === 'boolean') {
                    /** noop */
                } else {
                    this.processOne({ json: defValue, input: input, knownId: ident, identPrefix: parentName, localRefs, topName })
                }
            }
        }
        this.interfaces.push({
            name: parentName,
            members,
            source: input.relative,
            description: json.description,
            title: parentName !== json.title ? json.title : undefined
        })
    }

    processProperties (json, input, identPrefix, localRefs, parentName, topName) {
        /** @type {Member[]} */
        const members = []
        for (const [propName, value] of Object.entries(json.properties || {})) {
            const required = json.required?.includes(propName)
            if (typeof value === 'boolean') {
                /** Noop */
            } else {
                const thisId = value?.$id?.slice(14)
                const innerMembers = this.processObject({
                    value: value,
                    propName: propName,
                    ident: thisId,
                    input: input,
                    identPrefix,
                    localRefs,
                    parentName,
                    topName
                })
                const mapped = innerMembers.map(x => {
                    /** @type {Member} */
                    const memberMarkedAsRequired = { ...x, required }
                    return memberMarkedAsRequired
                })
                members.push(...mapped)
            }
        }
        return members
    }

    /**
     * @param {import('json-schema').JSONSchema7} value
     * @param {string} propName
     * @returns {Member[]} member
     */
    processString (value, propName) {
        /** @type {Member[]} */
        const members = []
        if (value.const) {
            members.push({
                name: propName,
                type: [JSON.stringify(value.const)],
                title: value.title,
                description: value.description
            })
        } else if (value.enum) {
            members.push({
                name: propName,
                type: [value.enum.map(x => JSON.stringify(x)).join(' | ')],
                title: value.title,
                description: value.description
            })
        } else { // base case
            members.push({
                name: propName,
                type: ['string'],
                title: value.title,
                description: value.description
            })
        }
        return members
    }

    /**
     * @param {ObjectArgs} args;
     * @returns {Member[]}
     */
    processObject (args) {
        const { value, propName, ident, input, identPrefix, localRefs, topName } = args
        /** @type {Member[]} */
        const members = []
        switch (value.type) {
        case 'string': {
            members.push(...this.processString(value, propName))
            break
        }
        case 'number': {
            members.push({ name: propName, type: ['number'] })
            break
        }
        case 'boolean': {
            members.push({ name: propName, type: ['boolean'] })
            break
        }
        case 'object': {
            if (Array.isArray(value.oneOf)) {
                const unionMembers = []
                for (const oneOfElement of value.oneOf) {
                    if (typeof oneOfElement === 'boolean') {
                        continue
                    }
                    if (oneOfElement?.$ref) {
                        const name = oneOfElement.$ref.slice(14)
                        unionMembers.push(name)
                    } else {
                        console.log('not Supported', value.oneOf)
                    }
                }
                members.push({
                    name: propName,
                    type: [formatUnionMembers(unionMembers)],
                    description: value.description
                })
            } else {
                if (ident) {
                    members.push({
                        name: propName,
                        type: [ident]
                    })
                }
                if (ident || propName === 'success') {
                    this.processOne({
                        json: value,
                        input: input,
                        topName
                    })
                }
            }
            break
        }
        case 'array': {
            members.push(...processArray(value, propName))
        }
        }
        members.push(...processUnknown(value, localRefs, args, propName, identPrefix))
        return members
    }
}

/**
 * @param {Input[]} inputs
 */
export function fromInputs (inputs) {
    /** @type {Group[]} */
    const groups = []

    for (const input of inputs) {
        const topRefs = Object.keys(input.json.definitions || {})
        const topName = input.json.$id?.slice(14)
        if (!topName) {
            throw new Error('unreachable')
        }
        const interfaces = new Parser({ json: input.json, input: input, localRefs: topRefs, topName }).parse()
        groups.push({ input, interfaces: interfaces.slice().reverse() })
    }

    const ajv = new Ajv({ schemas: inputs.map(x => x.json), code: { source: true }, strict: false })
    const moduleCode = standalone(ajv)
    return { validators: '// @ts-nocheck\n' + moduleCode, types: printJs(groups) }
}
