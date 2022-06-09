import { Matching, createMatching } from './matching'

const setFormHtml = (html) => {
    document.body.innerHTML = `
    <form>
        ${html}
    </form>
    `
    const formElement = document.querySelector('form')
    if (!formElement) throw new Error('unreachable')
    const inputs = Array.from(formElement?.querySelectorAll('input') || [])
    const selects = Array.from(formElement?.querySelectorAll('select') || [])
    const labels = Array.from(formElement?.querySelectorAll('label') || [])
    return {formElement, inputs: [...inputs, ...selects], labels}
}

beforeEach(() => {
    document.body.innerHTML = ''
})

describe('css-selector matching', () => {
    it.each([
        { html: `<input name=mail />`, selector: 'email', matched: true },
        { html: `<input name=oops! />`, selector: 'email', matched: false }
    ])(`$html: '$matched'`, (args) => {
        const { html, matched, selector } = args
        const { inputs } = setFormHtml(html)

        const matching = createMatching()
        const result = matching.execCssSelector(selector, inputs[0])
        expect(result.matched).toBe(matched)
    })
})

describe('ddg-matchers matching', () => {
    it.each([
        { html: `<input placeholder=email />`, matcher: 'email', matched: true },
        { html: `<input placeholder=mail />`, matcher: 'email', matched: false },
        { html: `<input placeholder=email-search />`, matcher: 'email', matched: false }
    ])(`$html: '$matcher': $matched`, (args) => {
        const { html, matched, matcher } = args
        const { inputs, formElement } = setFormHtml(html)

        const matching = createMatching()
        const result = matching
            .forInput(inputs[0], formElement)
            .execDDGMatcher(matcher)
        expect(result.matched).toBe(matched)
    })
})

describe('vendor-regexes matching', () => {
    it.each([
        { html: `<input name=email />`, regexName: 'email', matched: true },
        { html: `<input name=email-address />`, regexName: 'email', matched: true },
        { html: `<input name="courriel" />`, regexName: 'email', matched: true }, // fr
        { html: `<input name="メールアドレス" />`, regexName: 'email', matched: true } // ja-JP
    ])(`$html: '$regexName': $matched`, (args) => {
        const { html, matched, regexName } = args
        const { inputs, formElement } = setFormHtml(html)

        const matching = createMatching()
        const result = matching
            .forInput(inputs[0], formElement)
            .execVendorRegex(regexName)
        expect(result.matched).toBe(matched)
    })
})

describe('matching', () => {
    it('default config', () => {
        const matching = new Matching(Matching.emptyConfig)
        const {formElement, inputs} = setFormHtml(`<input name=email />`)
        const actual = matching.inferInputType(inputs[0], formElement)
        expect(actual).toBe('unknown')
    })
    it.each([
        { html: `<input name=mail />`, subtype: 'identities.emailAddress' },
        { html: `<input name="telefonnummer" value=0123456 />`, subtype: 'identities.phone' },
        { html: `<input name="電話" value=0123456 />`, subtype: 'identities.phone' },
        { html: `<input name="姓" value=0123456 />`, subtype: 'identities.lastName' },
        { html: `<input placeholder="password" />`, subtype: 'credentials.password' },
        { html: `<input placeholder="captcha-password" />`, subtype: 'unknown' },
        { html: `<input placeholder="username" />`, subtype: 'credentials.username' },
        { html: `<input name="username-search" />`, subtype: 'unknown' },
        { html: `<input name="cc-name" />`, subtype: 'creditCards.cardName' },
        { html: `<input name="accountholdername" /><!-- second input is to trigger cc type --><input name="cc-number"/>`, subtype: 'creditCards.cardName' },
        { html: `<input name="Срок действия карты" /><!-- second input is to trigger cc type --><input name="cc-number"/>`, subtype: 'creditCards.expirationMonth' },
        { html: `<input placeholder="ZIP code" autocomplete="shipping postal-code" type="text" name="checkout[shipping_address][zip]"/>`, subtype: 'identities.addressPostalCode' },
        { html: `<input autocomplete="on" id="address_line2" name="address_line2" type="text">`, subtype: 'identities.addressStreet2' },
        { html: `<input name="ADDRESS_LINE_1" type="text" aria-required="true" aria-describedby="ariaId_29" aria-labelledby="ariaId_30" autocomplete="off-street-address" aria-autocomplete="list">`, subtype: 'identities.addressStreet' },
        { html: `<input type="email" class="visuallyhidden" aria-hidden="true" tabindex="-1" style="">`, subtype: 'identities.emailAddress' },
        {
            html: `<select name="zipLookupCityState" class="form-dropdown-select" data-autom="form-field-zipLookupCityState" aria-required="true" aria-invalid="false"><option>1</option></select>`,
            subtype: 'identities.addressCity'
        },
        {
            html: `<div class="form_row">
                        <label for="shipping_address_two">Shipping address, line 2</label>
                        <input tabindex="104" type="text" class="text" maxlength="35" name="shipping_address_two" id="shipping_address_two">
                    </div>`,
            subtype: 'identities.addressStreet2'
        },
        {
            // This test has a new line between `First` and `name` -> which was previously not matching, but is now 😍
            html: `<input data-shane autocorrect="off" aria-labelledby="idms-input-labelledby-1643321390647-1" type="text" />
                    <span aria-hidden="true" id="idms-input-labelledby-1643321390647-1">First 
                    name</span>`,
            subtype: 'identities.firstName'
        },
        {
            // This test has a script tag between `First` and `name` -> which was previously not matching, but is now 😍
            html: `<input data-shane autocorrect="off" aria-labelledby="idms-input-labelledby-1643321390647-1" type="text" />
                    <span aria-hidden="true" id="idms-input-labelledby-1643321390647-1">First <script>console.log("hello world")</script>
                    name</span>`,
            subtype: 'identities.firstName'
        }

    ])(`$html should be '$subtype'`, (args) => {
        const { html, subtype } = args
        const { formElement, inputs } = setFormHtml(html)

        const matching = createMatching()
        const inferred = matching.inferInputType(inputs[0], formElement)
        expect(inferred).toBe(subtype)
    })
    it('should not continue past a ddg-matcher that has a "not" regex', () => {
        const {formElement, inputs} = setFormHtml(`<label>Email search<input name="email-search" /></label>`)
        const matching = new Matching({
            matchers: {
                lists: {
                    email: ['email']
                },
                fields: {
                    email: {
                        type: 'email',
                        strategies: {
                            ddgMatcher: 'email-ddg',
                            vendorRegex: 'email'
                        }
                    }
                }
            },
            strategies: {
                'vendorRegex': {
                    rules: {
                        email: null
                    },
                    ruleSets: [
                        {
                            email: 'email-'
                        }
                    ]
                },
                'ddgMatcher': {
                    matchers: {
                        'email-ddg': { match: 'email', forceUnknown: 'search' }
                    }
                },
                'cssSelector': {
                    selectors: {
                        'FORM_INPUTS_SELECTOR': 'input'
                    }
                }
            }
        })
        const asEmail = matching.inferInputType(inputs[0], formElement)
        /**
         * This should be 'unknown' because the negated 'search' regex in teh ddg-matcher should prevent
         * further strategies like the following vendor one
         */
        expect(asEmail).toBe('unknown')
    })
})
