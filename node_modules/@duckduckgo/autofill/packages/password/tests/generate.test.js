const { constants, _selectPasswordRules, HostnameInputError, ParserError, generate } = require('../')
const vendorRules = require('../rules.json')
const fc = require('fast-check')
const {Password} = require('../lib/apple.password')

function testUniqueTimes (domain, passwordRules, num = 10) {
    const pws = []
    for (let i = 0; i < num; i++) {
        // these 3 domains have rulesets so weak that collisions are likely
        if (domain === 'vivo.com.br') continue
        if (domain === 'allianz.com.br') continue
        if (domain === 'packageconciergeadmin.com') continue
        const pw = generate({input: passwordRules})
        pws.push(pw)
    }
    const asSet = new Set(pws)
    expect(asSet.size).toBe(pws.length)
    return pws
}

describe('password generation', () => {
    describe('public api', () => {
        it('creates rules with no arguments', () => {
            const defaultPw = generate()
            expect(defaultPw.length).toBeGreaterThanOrEqual(constants.DEFAULT_MIN_LENGTH)
            expect(defaultPw.length).toBeLessThanOrEqual(constants.DEFAULT_MAX_LENGTH)
        })
        it('creates from default rules', () => {
            const defaultPw = generate({input: constants.DEFAULT_PASSWORD_RULES})
            expect(defaultPw.length).toBeGreaterThanOrEqual(constants.DEFAULT_MIN_LENGTH)
            expect(defaultPw.length).toBeLessThanOrEqual(constants.DEFAULT_MAX_LENGTH)
        })
        it('creates matches snapshot requirements', () => {
            const pw = new Password()
            const { parameters } = pw.parse(constants.DEFAULT_PASSWORD_RULES)

            /**
             * This snapshot is added as a human-readable check that the internal params
             * are correct and are not changed by accident.
             */
            expect(parameters).toMatchInlineSnapshot(`
{
  "NumberOfRequiredRandomCharacters": 20,
  "PasswordAllowedCharacters": "abcdefghijkmnopqrstuvwxyzABCDEFGHIJKLMNPQRSTUVWXYZ0123456789-!#$%&?",
  "RequiredCharacterSets": [
    "-!#$%&?",
  ],
}
`)
        })
        it('handles any value for `input`', () => {
            fc.assert(
                fc.property(fc.anything(), (anything) => {
                    // @ts-ignore - this is deliberate
                    const pw = generate({ input: anything })
                    return typeof pw === 'string'
                })
            )
        })
        it('handles any value for `domain`', () => {
            fc.assert(
                fc.property(fc.anything(), (anything) => {
                    // @ts-ignore - this is deliberate
                    const pw = generate({ domain: anything })
                    return typeof pw === 'string'
                })
            )
        })
        it('handles any value for `onError`', () => {
            fc.assert(
                fc.property(fc.anything(), (anything) => {
                    // @ts-ignore - this is deliberate
                    const pw = generate({ onError: anything })
                    return typeof pw === 'string'
                })
            )
        })
        it('handles any value for `options`', () => {
            fc.assert(
                fc.property(fc.anything(), (anything) => {
                    // @ts-ignore - this is deliberate
                    const pw = generate(anything)
                    return typeof pw === 'string'
                })
            )
        })
        it('creates from vendor rules', () => {
            const password = generate({
                domain: 'example.com',
                rules: {
                    'example.com': {
                        'password-rules': 'minlength: 4; maxlength: 4;'
                    }
                }
            })
            expect(password.length).toBe(4)
        })
        it.each([
            { args: { input: 'invalid input' }, expectedErrorClass: ParserError },
            { args: { domain: 'localhost:8080' }, expectedErrorClass: HostnameInputError },
            { args: { domain: 'https://example.com' }, expectedErrorClass: HostnameInputError }
        ])('can receive errors', ({args, expectedErrorClass}) => {
            expect.assertions(1)
            generate({
                ...args,
                rules: vendorRules,
                onError (e) {
                    expect(e).toBeInstanceOf(expectedErrorClass)
                }
            })
        })
        it.each([
            { input: 'minlength: 30; maxlength: 40; required: upper; required: lower; required: [$]', test: (pws) => pws.every(pw => pw.includes('$')) },
            { input: 'minlength: 20; maxlength: 30; required: upper; required: lower;' },
            { input: 'required: upper;' }
        ])('generates from known inputs', ({input, test}) => {
            const pws = testUniqueTimes('none', input)
            if (test) {
                expect(test(pws)).toBeTruthy()
            }
        })
        it('uses DDG default password rules when inputs are not in the required format', () => {
            fc.assert(
                fc.property(fc.string(), data => {
                    const pw = generate({input: data})
                    return typeof pw === 'string' &&
                        pw.length >= constants.DEFAULT_MIN_LENGTH &&
                        pw.length <= constants.DEFAULT_MAX_LENGTH
                }),
                { seed: -1660958584, path: '0:0', endOnFailure: true }
            )
        })
    })
    describe('using vendor list', () => {
        it('_selectPasswordRules throws when a full URL is given', () => {
            expect.assertions(1)
            try {
                _selectPasswordRules('http://example.com', vendorRules)
            } catch (e) {
                expect(e).toBeInstanceOf(HostnameInputError)
            }
        })
        it('_selectPasswordRules throws when a host is given (with port)', () => {
            expect.assertions(1)
            try {
                _selectPasswordRules('localhost:8080', vendorRules)
            } catch (e) {
                expect(e).toBeInstanceOf(HostnameInputError)
            }
        })
        it('_selectPasswordRules throws when a URL cannot be constructed from input', () => {
            expect.assertions(1)
            try {
                _selectPasswordRules('', vendorRules)
            } catch (e) {
                expect(e).toBeInstanceOf(HostnameInputError)
            }
        })
        it('_selectPasswordRules returns undefined for a valid host with no match', () => {
            expect(_selectPasswordRules('example.com', {})).toBeUndefined()
        })
        it('_selectPasswordRules returns rules when its a direct match', () => {
            const actual = _selectPasswordRules('example.com', {
                'example.com': {
                    'password-rules': 'minlength: 20'
                }
            })

            expect(actual).toBe('minlength: 20')
        })
        it.each([
            'app.example.com',
            'app.app.app.app.example.com',
            'www.example.com'
        ])('_selectPasswordRules returns rules when its a subdomain match', (input) => {
            const actual = _selectPasswordRules(input, {
                'example.com': {
                    'password-rules': 'minlength: 20'
                }
            })
            expect(actual).toBe('minlength: 20')
        })
    })
    if (process.env.PASSWORD_STRESS_TEST) {
        describe('with valid inputs...', () => {
            let testCases = Object
                .entries(vendorRules)
                .map(([domain, value]) => ({domain, value}))

            it.each(testCases)('100 unique passwords for `$domain` ..', ({domain, value}) => {
                testUniqueTimes(domain, value['password-rules'], 100)
            })
            it.each(testCases.slice(0, 5))('10_000 unique passwords for `$domain` ..', ({domain, value}) => {
                testUniqueTimes(domain, value['password-rules'], 10_000)
            })
        })
    }
})
