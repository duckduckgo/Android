import { getExplicitLabelsText } from './matching'

const setFormHtml = (html) => {
    document.body.innerHTML = `<form>${html}</form>`
    const inputs = Array.from(document?.querySelectorAll('input') || [])
    return { inputs }
}

beforeEach(() => {
    document.body.innerHTML = ''
})

/**
 * Calling `getElementById()` with an empty string in FF produces console warnings
 * https://app.asana.com/0/892838074342800/1202094878508964/f
 */
describe('getExplicitLabelsText()', () => {
    let spy
    beforeEach(() => {
        spy = jest.spyOn(document, 'getElementById')
    })
    afterEach(() => {
        spy.mockClear()
    })
    it.each([
        `<input type="text">`,
        `<input type="text" aria-labelledby="">`,
        `<input type="text" aria-labelled="">`
    ])('does not call getElementById() with empty string', (html) => {
        const {inputs} = setFormHtml(html)
        getExplicitLabelsText(inputs[0])
        expect(spy).not.toHaveBeenCalled()
    })
    it.each([
        `
        <div id="label-div">First Name</div>
        <input type="text" aria-labelledby="label-div">
        `,
        `
        <div id="label-div">First Name</div>
        <input type="text" aria-labelled="label-div">
        `
    ])('calls getElementById() using string from `aria-labelledby` or `aria-labelled`', (html) => {
        const {inputs} = setFormHtml(html)
        getExplicitLabelsText(inputs[0])
        expect(spy).toHaveBeenCalledWith('label-div')
    })
})
