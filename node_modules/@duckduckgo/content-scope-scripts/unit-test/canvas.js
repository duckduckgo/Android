import ImageData from '@canvas/image-data'
import { modifyPixelData } from '../src/canvas.js'

function calculateCheckSum (imageData) {
    return imageData.data.reduce((t, v) => { return t + v }, 0)
}

// Produce some fake Image data, the values aren't really that important
function computeSampleImageData () {
    const height = 100
    const width = 100
    const inVal = []
    // Construct some fake pixel data
    for (let i = 0; i < width; i++) {
        for (let j = 0; j < height; j++) {
            // RGBA vals
            for (let k = 0; k < 4; k++) {
                inVal.push(i + j)
            }
        }
    }
    return new ImageData(new Uint8ClampedArray(inVal), height, width)
}

describe('Canvas', () => {
    it('Modifying a canvas should make a difference', () => {
        const imageData = computeSampleImageData()
        const inCS = calculateCheckSum(imageData)
        modifyPixelData(imageData, 'example.com', 'randomkey', 100)
        const outCS = calculateCheckSum(imageData)
        expect(inCS).not.toEqual(outCS)
    })

    it('Ensure image data from a different domain is unique', () => {
        const imageData = computeSampleImageData()
        modifyPixelData(imageData, 'example.com', 'randomkey', 100)
        const example = calculateCheckSum(imageData)

        const imageData2 = computeSampleImageData()
        modifyPixelData(imageData2, 'test.com', 'randomkey', 100)
        const test = calculateCheckSum(imageData2)

        expect(example).not.toEqual(test)
    })

    it('Ensure when key rotates the data output is unique', () => {
        const imageData = computeSampleImageData()
        modifyPixelData(imageData, 'example.com', 'randomkey', 100)
        const one = calculateCheckSum(imageData)

        const imageData2 = computeSampleImageData()
        modifyPixelData(imageData2, 'example.com', 'randomkey2', 100)
        const two = calculateCheckSum(imageData2)

        expect(one).not.toEqual(two)
    })
})
