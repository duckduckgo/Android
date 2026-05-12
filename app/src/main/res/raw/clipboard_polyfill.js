(function() {
    'use strict';

    if (window.__ddgClipboardPolyfillInstalled) {
        return;
    }
    window.__ddgClipboardPolyfillInstalled = true;

    if (!navigator.clipboard) {
        return;
    }

    if (typeof ddgClipboardObj === 'undefined') {
        return;
    }

    const originalWrite = navigator.clipboard.write;
    const pendingRequests = new Map();
    let requestId = 0;
    let blobId = 0;

    const instanceId = Math.random().toString(36).substring(2, 10);

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

    if (typeof OffscreenCanvas !== 'undefined' && OffscreenCanvas.prototype.convertToBlob) {
        const originalConvertToBlob = OffscreenCanvas.prototype.convertToBlob;
        OffscreenCanvas.prototype.convertToBlob = async function(options) {
            const canvas = this;
            return originalConvertToBlob.call(canvas, options);
        };
    }

    ddgClipboardObj.onmessage = function(event) {
        try {
            const response = JSON.parse(event.data);
            if (response.type === 'clipboardWriteResponse' && response.requestId !== undefined) {
                const pending = pendingRequests.get(response.requestId);
                if (pending) {
                    pendingRequests.delete(response.requestId);
                    if (response.success) {
                        pending.resolve();
                    } else {
                        pending.reject(new Error(response.error || 'Clipboard write failed'));
                    }
                }
            }
        } catch (e) {
            // Error parsing response
        }
    };

    function sendClipboardRequest(mimeType, base64Data) {
        return new Promise((resolve, reject) => {
            const id = requestId++;
            const timeoutMs = 10000;

            const timeoutId = setTimeout(() => {
                pendingRequests.delete(id);
                reject(new Error('Clipboard write timeout'));
            }, timeoutMs);

            pendingRequests.set(id, {
                resolve: () => {
                    clearTimeout(timeoutId);
                    resolve();
                },
                reject: (error) => {
                    clearTimeout(timeoutId);
                    reject(error);
                }
            });

            const message = JSON.stringify({
                type: 'clipboardWrite',
                requestId: id,
                instanceId: instanceId,
                mimeType: mimeType,
                data: base64Data
            });

            ddgClipboardObj.postMessage(message);
        });
    }

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
                                await sendClipboardRequest(type, captureToUse.dataUrl);
                                return Promise.resolve();
                            } else {
                                mostRecentCapture = null;
                            }
                        }

                        const blob = await item.getType(type);
                        const arrayBuffer = await blob.arrayBuffer();
                        const base64Data = arrayBufferToBase64DataUrl(arrayBuffer, type);
                        await sendClipboardRequest(type, base64Data);
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
