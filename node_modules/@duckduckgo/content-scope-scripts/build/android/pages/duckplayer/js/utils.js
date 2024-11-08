import { VideoParams } from 'injected/src/features/duckplayer/util.js';

/**
 * @param {string} href
 * @param {string} urlBase
 * @return {null | string}
 */
export function createYoutubeURLForError(href, urlBase) {
    const valid = VideoParams.forWatchPage(href);
    if (!valid) return null;

    // this will not throw, since it was guarded above
    const original = new URL(href);

    // for now, we're only intercepting clicks when `emb_err_woyt` is present
    // this may not be enough to cover all situations, but it solves our immediate
    // problems whilst keeping the blast radius low
    if (original.searchParams.get('feature') !== 'emb_err_woyt') return null;

    // if we get this far, we think a click is occurring that would cause a navigation loop
    // construct the 'next' url
    const url = new URL(urlBase);
    url.searchParams.set('v', valid.id);

    if (typeof valid.time === 'string') {
        url.searchParams.set('t', valid.time);
    }

    return url.toString();
}

/**
 * @param {string|null|undefined} iframeTitle
 * @return {string | null}
 */
export function getValidVideoTitle(iframeTitle) {
    if (typeof iframeTitle !== 'string') return null;
    if (iframeTitle === 'YouTube') return null;
    return iframeTitle.replace(/ - YouTube$/g, '');
}
