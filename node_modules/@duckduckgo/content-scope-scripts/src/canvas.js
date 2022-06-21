import { getDataKeySync } from './utils.js'
import Seedrandom from 'seedrandom'

/**
 * @param {HTMLCanvasElement} canvas
 * @param {string} domainKey
 * @param {string} sessionKey
 * @param {any} getImageDataProxy
 * @param {CanvasRenderingContext2D | WebGL2RenderingContext | WebGLRenderingContext} ctx?
 */
export function computeOffScreenCanvas (canvas, domainKey, sessionKey, getImageDataProxy, ctx) {
    if (!ctx) {
        ctx = canvas.getContext('2d')
    }

    // Make a off-screen canvas and put the data there
    const offScreenCanvas = document.createElement('canvas')
    offScreenCanvas.width = canvas.width
    offScreenCanvas.height = canvas.height
    const offScreenCtx = offScreenCanvas.getContext('2d')

    let rasterizedCtx = ctx
    // If we're not a 2d canvas we need to rasterise first into 2d
    const rasterizeToCanvas = !(ctx instanceof CanvasRenderingContext2D)
    if (rasterizeToCanvas) {
        rasterizedCtx = offScreenCtx
        offScreenCtx.drawImage(canvas, 0, 0)
    }

    // We *always* compute the random pixels on the complete pixel set, then pass back the subset later
    let imageData = getImageDataProxy._native.apply(rasterizedCtx, [0, 0, canvas.width, canvas.height])
    imageData = modifyPixelData(imageData, sessionKey, domainKey, canvas.width)

    if (rasterizeToCanvas) {
        clearCanvas(offScreenCtx)
    }

    offScreenCtx.putImageData(imageData, 0, 0)

    return { offScreenCanvas, offScreenCtx }
}

/**
 * Clears the pixels from the canvas context
 *
 * @param {CanvasRenderingContext2D} canvasContext
 */
function clearCanvas (canvasContext) {
    // Save state and clean the pixels from the canvas
    canvasContext.save()
    canvasContext.globalCompositeOperation = 'destination-out'
    canvasContext.fillStyle = 'rgb(255,255,255)'
    canvasContext.fillRect(0, 0, canvasContext.canvas.width, canvasContext.canvas.height)
    canvasContext.restore()
}

/**
 * @param {ImageData} imageData
 * @param {string} sessionKey
 * @param {string} domainKey
 * @param {number} width
 */
export function modifyPixelData (imageData, domainKey, sessionKey, width) {
    const d = imageData.data
    const length = d.length / 4
    let checkSum = 0
    const mappingArray = []
    for (let i = 0; i < length; i += 4) {
        if (!shouldIgnorePixel(d, i) && !adjacentSame(d, i, width)) {
            mappingArray.push(i)
            checkSum += d[i] + d[i + 1] + d[i + 2] + d[i + 3]
        }
    }

    const windowHash = getDataKeySync(sessionKey, domainKey, checkSum)
    const rng = new Seedrandom(windowHash)
    for (let i = 0; i < mappingArray.length; i++) {
        const rand = rng()
        const byte = Math.floor(rand * 10)
        const channel = byte % 3
        const pixelCanvasIndex = mappingArray[i] + channel

        d[pixelCanvasIndex] = d[pixelCanvasIndex] ^ (byte & 0x1)
    }

    return imageData
}

/**
 * Ignore pixels that have neighbours that are the same
 *
 * @param {Uint8ClampedArray} imageData
 * @param {number} index
 * @param {number} width
 */
function adjacentSame (imageData, index, width) {
    const widthPixel = width * 4
    const x = index % widthPixel
    const maxLength = imageData.length

    // Pixels not on the right border of the canvas
    if (x < widthPixel) {
        const right = index + 4
        if (!pixelsSame(imageData, index, right)) {
            return false
        }
        const diagonalRightUp = right - widthPixel
        if (diagonalRightUp > 0 && !pixelsSame(imageData, index, diagonalRightUp)) {
            return false
        }
        const diagonalRightDown = right + widthPixel
        if (diagonalRightDown < maxLength && !pixelsSame(imageData, index, diagonalRightDown)) {
            return false
        }
    }

    // Pixels not on the left border of the canvas
    if (x > 0) {
        const left = index - 4
        if (!pixelsSame(imageData, index, left)) {
            return false
        }
        const diagonalLeftUp = left - widthPixel
        if (diagonalLeftUp > 0 && !pixelsSame(imageData, index, diagonalLeftUp)) {
            return false
        }
        const diagonalLeftDown = left + widthPixel
        if (diagonalLeftDown < maxLength && !pixelsSame(imageData, index, diagonalLeftDown)) {
            return false
        }
    }

    const up = index - widthPixel
    if (up > 0 && !pixelsSame(imageData, index, up)) {
        return false
    }

    const down = index + widthPixel
    if (down < maxLength && !pixelsSame(imageData, index, down)) {
        return false
    }

    return true
}

/**
 * Check that a pixel at index and index2 match all channels
 * @param {Uint8ClampedArray} imageData
 * @param {number} index
 * @param {number} index2
 */
function pixelsSame (imageData, index, index2) {
    return imageData[index] === imageData[index2] &&
           imageData[index + 1] === imageData[index2 + 1] &&
           imageData[index + 2] === imageData[index2 + 2] &&
           imageData[index + 3] === imageData[index2 + 3]
}

/**
 * Returns true if pixel should be ignored
 * @param {Uint8ClampedArray} imageData
 * @param {number} index
 * @returns {boolean}
 */
function shouldIgnorePixel (imageData, index) {
    // Transparent pixels
    if (imageData[index + 3] === 0) {
        return true
    }
    return false
}
