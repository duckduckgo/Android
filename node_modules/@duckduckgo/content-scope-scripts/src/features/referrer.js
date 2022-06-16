import { overrideProperty } from '../utils'

export function init (args) {
    // Unfortunately, we only have limited information about the referrer and current frame. A single
    // page may load many requests and sub frames, all with different referrers. Since we
    if (args.referrer && // make sure the referrer was set correctly
        args.referrer.referrer !== undefined && // referrer value will be undefined when it should be unchanged.
        document.referrer && // don't change the value if it isn't set
        document.referrer !== '' && // don't add referrer information
        new URL(document.URL).hostname !== new URL(document.referrer).hostname) { // don't replace the referrer for the current host.
        let trimmedReferer = document.referrer
        if (new URL(document.referrer).hostname === args.referrer.referrerHost) {
            // make sure the real referrer & replacement referrer match if we're going to replace it
            trimmedReferer = args.referrer.referrer
        } else {
            // if we don't have a matching referrer, just trim it to origin.
            trimmedReferer = new URL(document.referrer).origin + '/'
        }
        overrideProperty('referrer', {
            object: Document.prototype,
            origValue: document.referrer,
            targetValue: trimmedReferer
        })
    }
}
