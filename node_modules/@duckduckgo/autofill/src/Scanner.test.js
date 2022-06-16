import { createScanner } from './Scanner'
import InterfacePrototype from './DeviceInterface/InterfacePrototype'

describe('performance', () => {
    beforeEach(() => {
        require('./requestIdleCallback')
        document.body.innerHTML = `
            <form action="">
                <label for="input-01"><input type="text" id="input-01"></label>
                <label for="input-02"><input type="text" id="input-02"></label>
                <label for="input-03"><input type="text" id="input-03"></label>
                <label for="input-04"><input type="text" id="input-04"></label>
                <label for="input-05"><input type="text" id="input-05"></label>
            </form>`
        jest.useFakeTimers()
    })
    afterAll(() => {
        jest.useRealTimers()
    })
    it('should debounce dom lookups', () => {
        const scanner = createScanner(InterfacePrototype.default())
        const spy = jest.spyOn(scanner, 'findEligibleInputs')
        scanner.enqueue([document])
        scanner.enqueue([document])
        scanner.enqueue([document])
        scanner.enqueue([document])
        scanner.enqueue([document])
        jest.advanceTimersByTime(1000)
        expect(spy).toHaveBeenCalledTimes(1)
    })
    it('should constrain the buffer size', () => {
        const scanner = createScanner(InterfacePrototype.default(), {
            bufferSize: 2
        })

        const spy = jest.spyOn(scanner, 'findEligibleInputs')

        // this will add 5 *different* elements to the queue
        let inputs = document.querySelectorAll('input')
        for (let input of inputs) {
            scanner.enqueue([input])
        }

        jest.advanceTimersByTime(1000)
        expect(spy).toHaveBeenCalledTimes(1)
    })
})
