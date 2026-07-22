(function() {
    'use strict';

    if (window.__ddgClipboardPolyfillInstalled) {
        return;
    }
    window.__ddgClipboardPolyfillInstalled = true;

    if (!navigator.clipboard) {
        return;
    }

    if (typeof DDGClipboard === 'undefined') {
        return;
    }

    const originalWrite = navigator.clipboard.write;
    let blobId = 0;

    let mostRecentCapture = null;

    const originalToBlob = HTMLCanvasElement.prototype.toBlob;
    HTMLCanvasElement.prototype.toBlob = function(callback, type, quality) {
        const canvas = this;
        const captureId = blobId++;

        try {
            const capturedDataUrl = canvas.toDataURL(type || 'image/png', quality);

            mostRecentCapture = {
                dataUrl: capturedDataUrl,
                captureId: captureId,
                timestamp: Date.now()
            };
        } catch (e) {
            // Capture failed, will fall back to blob
        }

        originalToBlob.call(canvas, callback, type, quality);
    };

    function arrayBufferToBase64DataUrl(arrayBuffer, mimeType) {
        const bytes = new Uint8Array(arrayBuffer);
        let binary = '';
        for (let i = 0; i < bytes.length; i++) {
            binary += String.fromCharCode(bytes[i]);
        }
        const base64 = btoa(binary);
        return 'data:' + mimeType + ';base64,' + base64;
    }

    navigator.clipboard.write = async function(items) {
        for (const item of items) {
            const types = item.types;

            for (const type of types) {
                if (type.startsWith('image/')) {
                    try {
                        if (mostRecentCapture && mostRecentCapture.dataUrl) {
                            const age = Date.now() - mostRecentCapture.timestamp;

                            if (age < 5000) {
                                const captureToUse = mostRecentCapture;
                                mostRecentCapture = null;

                                DDGClipboard.copyImageToClipboard(captureToUse.dataUrl, type);
                                return Promise.resolve();
                            } else {
                                mostRecentCapture = null;
                            }
                        }

                        const blob = await item.getType(type);
                        const arrayBuffer = await blob.arrayBuffer();
                        const base64Data = arrayBufferToBase64DataUrl(arrayBuffer, type);

                        DDGClipboard.copyImageToClipboard(base64Data, type);
                        return Promise.resolve();
                    } catch (e) {
                        // Error processing image
                    }
                }
            }
        }

        if (originalWrite) {
            return originalWrite.call(navigator.clipboard, items);
        }
        return Promise.reject(new Error('Clipboard write not supported'));
    };
})();
