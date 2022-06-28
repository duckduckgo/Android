import InterfacePrototype from '../DeviceInterface/InterfacePrototype'
import { createScanner } from '../Scanner'
import {attachAndReturnGenericForm} from '../test-utils'

afterEach(() => {
    document.body.innerHTML = ''
})

describe('Test the form class reading values correctly', () => {
    const testCases = [
        {
            testCase: 'form with username',
            form: `
<form>
    <input type="text" value="testUsername" autocomplete="username" />
    <input type="password" value="testPassword" autocomplete="new-password" />
    <button type="submit">Sign up</button>
</form>`,
            expHasValues: true,
            expValues: {credentials: {username: 'testUsername', password: 'testPassword'}}
        },
        {
            testCase: 'form with email',
            form: `
<form>
    <input type="email" value="name@email.com" autocomplete="email" />
    <input type="password" value="testPassword" autocomplete="new-password" />
    <button type="submit">Sign up</button>
</form>`,
            expHasValues: true,
            expValues: {credentials: {username: 'name@email.com', password: 'testPassword'}}
        },
        {
            testCase: 'form with both email and username fields',
            form: `
<form>
    <input type="text" value="testUsername" autocomplete="username" />
    <input type="email" value="name@email.com" autocomplete="email" />
    <input type="password" value="testPassword" autocomplete="new-password" />
    <button type="submit">Sign up</button>
</form>`,
            expHasValues: true,
            expValues: {credentials: {username: 'testUsername', password: 'testPassword'}}
        },
        {
            testCase: 'form with readonly email fields and password',
            form: `
<form>
    <input type="email" value="name@email.com" autocomplete="email" readonly />
    <input type="password" value="testPassword" autocomplete="new-password" />
    <button type="submit">Sign up</button>
</form>`,
            expHasValues: true,
            expValues: {credentials: {username: 'name@email.com', password: 'testPassword'}}
        },
        {
            testCase: 'form with empty fields',
            form: `
<form>
    <input type="text" value="" autocomplete="username" />
    <input type="password" value="" autocomplete="new-password" />
    <button type="submit">Sign up</button>
</form>`,
            expHasValues: false,
            expValues: {credentials: undefined}
        },
        {
            testCase: 'form with only the password filled',
            form: `
<form>
    <input type="text" value="" autocomplete="username" />
    <input type="password" value="testPassword" autocomplete="new-password" />
    <button type="submit">Sign up</button>
</form>`,
            expHasValues: true,
            expValues: {credentials: {password: 'testPassword'}}
        },
        {
            testCase: 'form with only the username filled',
            form: `
<form>
    <input type="text" value="testUsername" autocomplete="username" />
    <input type="password" value="" autocomplete="new-password" />
    <button type="submit">Sign up</button>
</form>`,
            expHasValues: false,
            expValues: {credentials: undefined}
        },
        {
            testCase: 'complete checkout form',
            form: `
<form method="post" id="usrForm">
    <fieldset>
        <legend>Contact Info</legend>
        <label for="frmNameA">Name</label>
        <input name="name" id="frmNameA" placeholder="Full name" autocomplete="name" value="Peppa Pig">
        <label for="frmEmailA">Email</label>
        <input type="email" name="email" id="frmEmailA" placeholder="name@example.com" autocomplete="email" value="peppapig@email.com">
        <label for="frmEmailC">Confirm Email</label>
        <input type="email" name="emailC" id="frmEmailC" placeholder="name@example.com" autocomplete="email" value="peppapig@email.com">
        <label for="frmPhoneNumA">Phone</label>
        <input type="tel" name="phone" id="frmPhoneNumA" placeholder="+1-650-450-1212" autocomplete="tel" value="6100000000">
    </fieldset>
    
    <fieldset>
        <legend>Billing</legend>
        <label for="frmAddressB">Address</label>
        <input name="bill-address" id="frmAddressB" placeholder="123 Any Street" autocomplete="billing street-address" value="604 Redford St">
        <label for="frmCityB">City</label>
        <input name="bill-city" id="frmCityB" placeholder="New York" autocomplete="billing address-level2" value="Farmville">
        <label for="frmStateB">State</label>
        <input name="bill-state" id="frmStateB" placeholder="NY" autocomplete="billing address-level1" value="Virginia">
        <label for="frmZipB">Zip</label>
        <input name="bill-zip" id="frmZipB" placeholder="10011" autocomplete="billing postal-code" value="23901">
        <label for="frmCountryB">Country</label>
        <input name="bill-country" id="frmCountryB" placeholder="USA" autocomplete="billing country" value="United States">
    </fieldset>

    <fieldset>
        <legend>Payment</legend>
        <label for="frmNameCC">Name on card</label>
        <input name="ccname" id="frmNameCC" placeholder="Full Name" autocomplete="cc-name" value="Peppa Pig">
        <label for="frmCCNum">Card Number</label>
        <input name="cardnumber" id="frmCCNum" autocomplete="cc-number" value="4111111111111111">
        <label for="frmCCCVC">CVC</label>
        <input name="cvc" id="frmCCCVC" autocomplete="cc-csc" value="123">
        <label for="frmCCExp">Expiry</label>
        <input name="cc-exp" id="frmCCExp" placeholder="MM-YYYY" autocomplete="cc-exp" value="12-2028">
  
        <button class="btn" id="butCheckout">Check Out</button>
    </fieldset>
</form>`,
            expHasValues: true,
            expValues: {
                identities: {
                    firstName: 'Peppa',
                    lastName: 'Pig',
                    addressStreet: '604 Redford St',
                    addressCity: 'Farmville',
                    addressProvince: 'Virginia',
                    addressPostalCode: '23901',
                    addressCountryCode: 'US',
                    phone: '6100000000',
                    emailAddress: 'peppapig@email.com'
                },
                creditCards: {
                    cardName: 'Peppa Pig',
                    cardSecurityCode: '123',
                    expirationMonth: '12',
                    expirationYear: '2028',
                    cardNumber: '4111111111111111'
                }
            }
        },
        {
            testCase: 'test localised country code with text input',
            form: `
<form lang="it">
    <input value="Peppa Pig" autocomplete="name" />
    <input value="via Gioberti 41" autocomplete="street-address" />
    <input value="Macerata" autocomplete="address-level2" />
    <input value="Italia" autocomplete="country" />
</form>`,
            expHasValues: true,
            expValues: {identities: {addressCountryCode: 'IT'}}
        },
        {
            testCase: 'incomplete identities form',
            form: `
<form>
    <input value="Macerata" autocomplete="address-level2" />
    <input value="Italia" autocomplete="country" />
</form>`,
            expHasValues: false,
            expValues: {identities: undefined}
        },
        {
            testCase: 'incomplete creditCard form',
            form: `
<form>
    <input autocomplete="cc-name" value="Peppa Pig">
    <input autocomplete="cc-number" value="4111111111111111">
</form>`,
            expHasValues: false,
            expValues: {creditCards: undefined}
        },
        {
            testCase: 'creditCard form with all values except cvv',
            form: `
<form>
    <label for="frmNameCC">Name on card</label>
    <input name="ccname" id="frmNameCC" placeholder="Full Name" autocomplete="cc-name" value="Peppa Pig">
    <label for="frmCCNum">Card Number</label>
    <input name="cardnumber" id="frmCCNum" autocomplete="cc-number" value="4111111111111111">
    <label for="frmCCExp">Expiry</label>
    <input name="cc-exp" id="frmCCExp" placeholder="YYYY-MM" autocomplete="cc-exp" value="2028-12">
</form>`,
            expHasValues: true,
            expValues: {
                creditCards: {
                    cardName: 'Peppa Pig',
                    expirationMonth: '12',
                    expirationYear: '2028',
                    cardNumber: '4111111111111111'
                }
            }
        },
        {
            testCase: 'creditCard form with all values but name and identities name in adjacent field',
            form: `
<form method="post" id="usrForm">
    <fieldset>
        <legend>Contact Info</legend>
        <label for="frmNameA">Name</label>
        <input name="name" id="frmNameA" placeholder="Full name" autocomplete="name" value="Peppa Pig">
        <label for="frmEmailA">Email</label>
        <input type="email" name="email" id="frmEmailA" placeholder="name@example.com" autocomplete="email" value="peppapig@email.com">
    </fieldset>

    <fieldset>
        <legend>Payment</legend>
        <label for="frmCCNum">Card Number</label>
        <input name="cardnumber" id="frmCCNum" autocomplete="cc-number" value="4111111111111111">
        <label for="frmCCCVC">CVC</label>
        <input name="cvc" id="frmCCCVC" autocomplete="cc-csc" value="123">
        <label for="frmCCExp">Expiry</label>
        <input name="cc-exp" id="frmCCExp" placeholder="MM-YYYY" autocomplete="cc-exp" value="12-2028">
        
        <button class="btn" id="butCheckout">Check Out</button>
    </fieldset>
</form>`,
            expHasValues: true,
            expValues: {
                identities: undefined,
                creditCards: {
                    cardName: 'Peppa Pig',
                    cardSecurityCode: '123',
                    expirationMonth: '12',
                    expirationYear: '2028',
                    cardNumber: '4111111111111111'
                }
            }
        }
    ]

    test.each(testCases)('Test $testCase', (
        {
            form,
            expHasValues,
            expValues
        }) => {
        const formEl = attachAndReturnGenericForm(form)
        const scanner = createScanner(InterfacePrototype.default()).findEligibleInputs(document)
        const formClass = scanner.forms.get(formEl)
        const hasValues = formClass?.hasValues()
        const formValues = formClass?.getValues()

        expect(hasValues).toBe(expHasValues)
        expect(formValues).toMatchObject(expValues)
    })
})

describe('Form validity is reported correctly', () => {
    const testCases = [
        {
            testCase: 'valid form with validation',
            form: `
<form>
    <input autocomplete="cc-name" required value="Peppa Pig">
    <input autocomplete="cc-number" required value="4111111111111111">
</form>`,
            expIsValid: true
        },
        {
            testCase: 'valid form because of no validation',
            form: `
<form>
    <input autocomplete="cc-name" value="Peppa Pig">
    <input autocomplete="cc-number" value="4111111111111111">
</form>`,
            expIsValid: true
        },
        {
            testCase: 'valid non-standard form',
            form: `
<div id="form">
    <input autocomplete="cc-name" required value="Peppa Pig">
    <input autocomplete="cc-number" required value="4111111111111111">
</div>`,
            expIsValid: true
        },
        {
            testCase: 'invalid form',
            form: `
<form>
    <input autocomplete="cc-name" required value="">
    <input autocomplete="cc-number" minlength="10" value="4111">
</form>`,
            expIsValid: false
        },
        {
            testCase: 'invalid non-standard form',
            form: `
<div id="form">
    <input autocomplete="cc-name" required value="">
    <input autocomplete="cc-number" required value="">
</div>`,
            expIsValid: false
        },
        {
            testCase: 'invalid non-standard form because of invalid undecorated field',
            form: `
<div id="form">
    <input autocomplete="cc-name" value="">
    <input autocomplete="cc-number" value="">
    <input type="text" required value="">
</div>`,
            expIsValid: false
        }
    ]

    test.each(testCases)('Test $testCase', (
        {
            form,
            expIsValid
        }) => {
        const formEl = attachAndReturnGenericForm(form)

        const scanner = createScanner(InterfacePrototype.default()).findEligibleInputs(document)

        const formClass = scanner.forms.get(formEl)
        const isValid = formClass?.isValid()

        expect(isValid).toBe(expIsValid)
    })
})

describe('Check form has focus', () => {
    test('focus detected correctly', () => {
        const formEl = attachAndReturnGenericForm()

        // When we require autofill, the script scores the fields in the DOM
        const scanner = createScanner(InterfacePrototype.default()).findEligibleInputs(document)

        const formClass = scanner.forms.get(formEl)

        expect(formClass?.hasFocus()).toBe(false)

        const input = formEl.querySelector('input')
        input?.focus()

        expect(formClass?.hasFocus()).toBe(true)

        input?.blur()

        expect(formClass?.hasFocus()).toBe(false)
    })
})
