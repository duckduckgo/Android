import fs from 'fs'
import path from 'path'
import { getUnifiedExpiryDate } from './formatters'
import { createScanner } from '../Scanner'
import { getInputSubtype, createMatching } from './matching'
import { Form } from './Form'
import InterfacePrototype from '../DeviceInterface/InterfacePrototype'

import testCases from './test-cases/index'

/**
 * @param {HTMLInputElement} el
 * @param {HTMLFormElement} form
 * @returns {string|undefined}
 */
const getCCFieldSubtype = (el, form) => {
    const matching = createMatching()

    return matching
        .forInput(el, form)
        .subtypeFromMatchers('cc', el)
}

const renderInputWithLabel = () => {
    const input = document.createElement('input')
    input.id = 'inputId'
    const label = document.createElement('label')
    label.setAttribute('for', 'inputId')
    const formElement = document.createElement('form')
    formElement.append(input, label)
    document.body.append(formElement)
    const form = new Form(formElement, input, InterfacePrototype.default())
    return { input, label, formElement: formElement, form }
}

const testRegexForCCLabels = (cases) => {
    Object.entries(cases).forEach(([expectedType, arr]) => {
        arr.forEach(({text, shouldMatch = true}) => {
            it(`"${text}" should ${shouldMatch ? '' : 'not '}match regex for ${expectedType}`, () => {
                const {input, label, formElement} = renderInputWithLabel()
                label.textContent = text

                const subtype = getCCFieldSubtype(input, formElement)
                if (shouldMatch) {
                    expect(subtype).toBe(expectedType)
                } else {
                    expect(subtype).not.toBe(expectedType)
                }
            })
        })
    })
}

afterEach(() => {
    document.body.innerHTML = ''
})

describe('Input Classifiers', () => {
    const ccLabelTestCases = {
        cardName: [
            {text: 'credit card name'},
            {text: 'name on card'},
            {text: 'card holder'},
            {text: 'card owner'},
            {text: 'card number', shouldMatch: false}
        ],
        cardNumber: [
            {text: 'Credit Card Number'},
            {text: 'number on card'},
            {text: 'card owner', shouldMatch: false}
        ],
        expirationMonth: [
            {text: 'expiry month'},
            {text: 'expiration month'},
            {text: 'exp month'},
            {text: 'Credit Card Number', shouldMatch: false},
            {text: 'expiry year', shouldMatch: false},
            {text: 'expiration year', shouldMatch: false},
            {text: 'exp year', shouldMatch: false},
            {text: 'card expiry yy', shouldMatch: false}
        ],
        expirationYear: [
            {text: 'expiry year'},
            {text: 'expiration year'},
            {text: 'exp year'},
            {text: 'card expiry yy'},
            {text: 'Credit Card Number', shouldMatch: false},
            {text: 'expiry month', shouldMatch: false},
            {text: 'expiration month', shouldMatch: false},
            {text: 'exp month', shouldMatch: false},
            {text: 'card expiry mo', shouldMatch: false}
        ]
    }
    testRegexForCCLabels(ccLabelTestCases)

    describe('Unified Expiration Date', () => {
        describe.each([
            { text: 'mm-yyyy', expectedResult: '08-2025' },
            { text: 'mm/yyyy', expectedResult: '08/2025' },
            { text: '__-____', expectedResult: '08-2025' },
            { text: 'mm-yy', expectedResult: '08-25' },
            { text: 'i.e. 10-2022', expectedResult: '08-2025' },
            { text: 'MM-AAAA', expectedResult: '08-2025' },
            { text: 'mm_jj', expectedResult: '08_25' },
            { text: 'mm.yy', expectedResult: '08.25' },
            { text: 'mm - yy', expectedResult: '08-25' },
            { text: 'mm yy', expectedResult: '08 25' },
            { text: 'ie: 08.22', expectedResult: '08.25' }
        ])('when checking for "$text"', ({ text, expectedResult }) => {
            let elements

            beforeEach(() => {
                elements = renderInputWithLabel()
                elements.input.autocomplete = 'cc-exp'
            })

            it('matches for placeholder text', () => {
                elements.input.placeholder = text

                expect(getCCFieldSubtype(elements.input, elements.form)).toBe('expiration')
                expect(getUnifiedExpiryDate(elements.input, '8', '2025', elements.form)).toBe(expectedResult)
            })

            it('matches for label text', () => {
                elements.label.textContent = text

                expect(getCCFieldSubtype(elements.input, elements.form)).toBe('expiration')
                expect(getUnifiedExpiryDate(elements.input, '8', '2025', elements.form)).toBe(expectedResult)
            })
        })
    })
})
let testResults = []

describe.each(testCases)('Test $html fields', (testCase) => {
    const { html, expectedFailures = [], expectedSubmitFalsePositives = 0, expectedSubmitFalseNegatives = 0, title = '__test__' } = testCase

    const testTextString = expectedFailures.length > 0
        ? `should contain ${expectedFailures.length} known failure(s): ${JSON.stringify(expectedFailures)}`
        : `should NOT contain failures`

    it(testTextString, () => {
        const testContent = fs.readFileSync(path.resolve(__dirname, './test-cases', html), 'utf-8')

        document.body.innerHTML = testContent
        document.title = title

        const scanner = createScanner(InterfacePrototype.default())
        scanner.findEligibleInputs(document)

        const detectedSubmitButtons = Array.from(scanner.forms.values()).map(form => form.submitButtons).flat()
        /**
         * @type {HTMLElement[]}
         */
        const identifiedSubmitButtons = Array.from(document.querySelectorAll('[data-manual-submit]'))

        let submitFalsePositives = detectedSubmitButtons.filter(button => !identifiedSubmitButtons.includes(button)).length
        let submitFalseNegatives = identifiedSubmitButtons.filter(button => !detectedSubmitButtons.includes(button)).length

        expect(submitFalsePositives).toEqual(expectedSubmitFalsePositives)
        expect(submitFalseNegatives).toEqual(expectedSubmitFalseNegatives)

        /**
         * @type {NodeListOf<HTMLInputElement>}
         */
        const manuallyScoredFields = document.querySelectorAll('[data-manual-scoring]')

        const scores = Array.from(manuallyScoredFields).map(field => {
            const { manualScoring, ddgInputtype, ...rest } = field.dataset
            // @ts-ignore
            field.style = ''
            return {
                attrs: {
                    name: field.name,
                    id: field.id,
                    dataset: rest
                },
                html: field.outerHTML,
                inferredType: getInputSubtype(field),
                manualScore: field.getAttribute('data-manual-scoring')
            }
        })

        const submitButtonScores = {
            detected: detectedSubmitButtons.length,
            identified: identifiedSubmitButtons.length,
            falsePositives: submitFalsePositives,
            falseNegatives: submitFalseNegatives
        }

        testResults.push({ testCase, scores, submitButtonScores })

        let bad = scores.filter(x => x.inferredType !== x.manualScore)
        let failed = bad.map(x => x.manualScore)

        if (bad.length !== expectedFailures.length) {
            for (let score of bad) {
                console.log(
                    'manualType:   ' + JSON.stringify(score.manualScore),
                    '\ninferredType: ' + JSON.stringify(score.inferredType),
                    '\nid:          ', JSON.stringify(score.attrs.id),
                    '\nname:        ', JSON.stringify(score.attrs.name),
                    '\nHTML:        ', score.html
                )
            }
        }
        expect(failed).toStrictEqual(expectedFailures)
    })
})

afterAll(() => {
    /* site statistics
    a site is considered "failing" if there is at least one failing field in at least one of its tests
    (including expected failures)
     */

    let siteHasFailures = {}

    testResults.forEach((result) => {
        const siteName = result.testCase.html.split('_')[0]
        const testHasFailures = result.scores.some(field => field.manualScore !== field.inferredType)
        if (siteHasFailures[siteName] !== true) {
            siteHasFailures[siteName] = testHasFailures
        }
    })

    const proportionFailingSites = Object.values(siteHasFailures).filter(t => t === true).length / Object.values(siteHasFailures).length

    /* field statistics */

    let totalFields = 0
    let totalFailedFields = 0
    let totalFalsePositives = 0

    let totalFieldsByType = {}
    let totalFailuresByFieldType = {}

    testResults.forEach((result) => {
        result.scores.forEach((field) => {
            if (!totalFieldsByType[field.manualScore]) {
                totalFieldsByType[field.manualScore] = 0
                totalFailuresByFieldType[field.manualScore] = 0
            }

            if (field.manualScore !== field.inferredType) {
                totalFailedFields++
                totalFailuresByFieldType[field.manualScore]++
            }
            if (field.manualScore === 'unknown' && field.inferredType !== field.manualScore) {
                totalFalsePositives++
            }
            totalFields++
            totalFieldsByType[field.manualScore]++
        })
    })

    console.log(
        'Input classification statistics:',
        '\n% of failing sites:\t\t' + Math.round(proportionFailingSites * 100) + '%',
        '\n% of failing fields:\t' + Math.round((totalFailedFields / totalFields) * 100) + '%',
        '\n% of false positive fields:\t' + Math.round((totalFalsePositives / totalFields) * 100) + '%',
        '\n% fields failing by type:',
        '\n' + Object.keys(totalFieldsByType).sort().map((type) => {
            return '\n' + (type + ':').padEnd(24) +
                    (Math.round((totalFailuresByFieldType[type] / totalFieldsByType[type]) * 100) + '%').padEnd(4) +
                    ' | ' + String(totalFailuresByFieldType[type]).padStart(4) +
                    ' out of ' + String(totalFieldsByType[type]).padStart(4) + ' fields | ' +
                    ' (' + Math.round((totalFailuresByFieldType[type] / totalFailedFields) * 100) + '% of all failures)'
        }).join('') + '\n'
    )

    let totalDetectedButtons = testResults.map(test => test.submitButtonScores.detected).reduce((a, b) => a + b)
    let totalIdentifiedButtons = testResults.map(test => test.submitButtonScores.identified).reduce((a, b) => a + b)
    let totalFalsePositiveButtons = testResults.map(test => test.submitButtonScores.falsePositives).reduce((a, b) => a + b)
    let totalFalseNegativeButtons = testResults.map(test => test.submitButtonScores.falseNegatives).reduce((a, b) => a + b)

    console.log(
        'Submit button statistics:\n',
        totalDetectedButtons + ' detected (' + Math.round((totalFalsePositiveButtons / totalDetectedButtons) * 100) + '% false positive)\n',
        totalIdentifiedButtons + ' manually identified (' + Math.round((totalFalseNegativeButtons / totalIdentifiedButtons) * 100) + '% false negative)\n'
    )
})
