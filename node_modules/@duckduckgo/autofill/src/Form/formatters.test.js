import { getMMAndYYYYFromString } from './formatters'

describe('Format year and month from single string', () => {
    const testCases = [
        '05-2028',
        '05/2028',
        '05/28',
        '05 28',

        '5-2028',
        '5/2028',
        '5/28',
        '5 28',

        '05 - 2028',
        '05 / 2028',
        '05 / 28',
        '05  28',

        '5 - 2028',
        '5 / 2028',
        '5 / 28',
        '5  28',

        '2028-05',
        '2028/05',
        '28/05',
        '28 05',

        '2028-5',
        '2028/5',
        '28/5',
        '28 5',

        '2028 - 05',
        '2028 / 05',
        '28 / 05',
        '28  05',

        '2028 - 5',
        '2028 / 5',
        '28 / 5',
        '28  5'
    ]

    test.each(testCases)('Test for "%s"', (expiry) => {
        const result = getMMAndYYYYFromString(expiry)
        expect(result).toMatchObject({
            expirationMonth: '05',
            expirationYear: '2028'
        })
    })
})
