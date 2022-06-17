import { fromInputs } from '../src/messaging/codegen.js'
import { printComments, printInterfaceHeading, printMember } from '../src/messaging/codegen-printers.js'

fdescribe('code generation', () => {
    describe('for a message', () => {
        it('should generate types + message implementation', () => {
            const requestSchema = {
                $schema: 'http://json-schema.org/draft-07/schema#',
                $id: '#/definitions/GetAutofillDataRequest',
                title: 'Request Object',
                type: 'object',
                meta: {
                    name: 'getAutofillData',
                    type: 'request'
                },
                properties: {
                    mainType: { $ref: '#/definitions/Other' }
                },
                required: ['mainType'],
                definitions: {
                    Other: {
                        type: 'object',
                        properties: {
                            name: { type: 'string' }
                        }
                    }
                }
            }
            const responseSchema = {
                $schema: 'http://json-schema.org/draft-07/schema#',
                $id: '#/definitions/GetAutofillDataResponse',
                title: 'Response Object',
                type: 'object',
                meta: {
                    name: 'getAutofillData',
                    type: 'response'
                },
                properties: {
                    success: {
                        $id: '#/definitions/AutofillData',
                        type: 'object',
                        properties: {
                            username: { type: 'string' },
                            password: { type: 'string' },
                            other: { $ref: '#/definitions/Other' }
                        },
                        required: ['username']
                    }
                },
                required: ['success'],
                definitions: {
                    Other: {
                        type: 'object',
                        properties: {
                            id: { type: 'string', enum: ['01', '02'] }
                        }
                    }
                }
            }
            /**
             * @type {import('../src/messaging/codegen').Input[]}
             */
            const inputs = [
                // @ts-ignore
                { json: requestSchema, relative: '01.json' },
                // @ts-ignore
                { json: responseSchema, relative: '02.json' }
            ]
            const { types } = fromInputs(inputs)
            const expected = `// Do not edit, this was created by \`scripts/schema.js\`
/**
 * @link {import("./01.json")}
 * @typedef GetAutofillDataRequest Request Object
 * @property {GetAutofillDataRequestOther} mainType
 */
/**
 * @link {import("./01.json")}
 * @typedef GetAutofillDataRequestOther
 * @property {string} [name]
 */
/**
 * @link {import("./02.json")}
 * @typedef GetAutofillDataResponse Response Object
 * @property {AutofillData} success
 */
/**
 * @link {import("./02.json")}
 * @typedef GetAutofillDataResponseOther
 * @property {"01" | "02"} [id]
 */
/**
 * @link {import("./02.json")}
 * @typedef AutofillData
 * @property {string} username
 * @property {string} [password]
 * @property {Other} [other]
 */
`
            expect(types).toBe(expected)
        })
    })
    describe('when printing', () => {
        describe('interface headings', () => {
            it('should print with type inline when it is a record', () => {
                const lines = printInterfaceHeading({
                    name: 'Foo',
                    title: 'A title',
                    description: 'A Description',
                    source: 'hello',
                    members: [
                        { name: '[index: string]', type: ['Settings'] }
                    ]
                })
                expect(lines[0]).toBe('@typedef {Record<string, Settings>} Foo A title A Description')
            })
            it('should not print type where there are multiple members', () => {
                const lines = printInterfaceHeading({
                    name: 'Foo',
                    title: 'A title',
                    description: 'A Description',
                    source: 'hello',
                    members: [
                        { name: 'bar', type: ['Bar'] },
                        { name: 'baz', type: ['Baz'] }
                    ]
                })
                expect(lines[0]).toBe('@typedef Foo A title A Description')
            })
            it('should split the description over 2 lines', () => {
                const lines = printInterfaceHeading({
                    name: 'Foo',
                    title: 'A title',
                    description: 'A Description\nover two lines',
                    source: 'hello',
                    members: [
                        { name: 'bar', type: ['Bar'] },
                        { name: 'baz', type: ['Baz'] }
                    ]
                })
                expect(lines[0]).toBe('@typedef Foo A title')
                expect(lines[1]).toBe('A Description')
                expect(lines[2]).toBe('over two lines')
            })
        })
        describe('comments', () => {
            it('should print lines', () => {
                const actual = printComments(['foo', 'bar', 'baz'])
                expect(actual).toBe(`/**
 * foo
 * bar
 * baz
 */`)
                console.log(actual)
            })
        })
        describe('members', () => {
            it('prints when required', () => {
                const lines = printMember({
                    name: 'bar',
                    type: ['Bar'],
                    required: true
                })
                expect(lines[0]).toBe('@property {Bar} bar')
            })
            it('prints when optional', () => {
                const lines = printMember({
                    name: 'bar',
                    type: ['Bar'],
                    required: false
                })
                expect(lines[0]).toBe('@property {Bar} [bar]')
            })
            // it('prints enum', () => {
            //     const lines = printMember({
            //         name: 'bar',
            //         type: ['Bar', 'Foo', 'baz'],
            //         required: false
            //     })
            //     expect(lines[0]).toBe('@property {Bar | Foo | baz} [bar]')
            // })
        })
    })
})
