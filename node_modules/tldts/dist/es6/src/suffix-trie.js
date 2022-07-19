import { fastPathLookup, } from 'tldts-core';
import { exceptions, rules } from './data/trie';
/**
 * Lookup parts of domain in Trie
 */
function lookupInTrie(parts, trie, index, allowedMask) {
    let result = null;
    let node = trie;
    while (node !== undefined) {
        // We have a match!
        if ((node.$ & allowedMask) !== 0) {
            result = {
                index: index + 1,
                isIcann: node.$ === 1 /* RULE_TYPE.ICANN */,
                isPrivate: node.$ === 2 /* RULE_TYPE.PRIVATE */,
            };
        }
        // No more `parts` to look for
        if (index === -1) {
            break;
        }
        const succ = node.succ;
        node = succ && (succ[parts[index]] || succ['*']);
        index -= 1;
    }
    return result;
}
/**
 * Check if `hostname` has a valid public suffix in `trie`.
 */
export default function suffixLookup(hostname, options, out) {
    if (fastPathLookup(hostname, options, out) === true) {
        return;
    }
    const hostnameParts = hostname.split('.');
    const allowedMask = (options.allowPrivateDomains === true ? 2 /* RULE_TYPE.PRIVATE */ : 0) |
        (options.allowIcannDomains === true ? 1 /* RULE_TYPE.ICANN */ : 0);
    // Look for exceptions
    const exceptionMatch = lookupInTrie(hostnameParts, exceptions, hostnameParts.length - 1, allowedMask);
    if (exceptionMatch !== null) {
        out.isIcann = exceptionMatch.isIcann;
        out.isPrivate = exceptionMatch.isPrivate;
        out.publicSuffix = hostnameParts.slice(exceptionMatch.index + 1).join('.');
        return;
    }
    // Look for a match in rules
    const rulesMatch = lookupInTrie(hostnameParts, rules, hostnameParts.length - 1, allowedMask);
    if (rulesMatch !== null) {
        out.isIcann = rulesMatch.isIcann;
        out.isPrivate = rulesMatch.isPrivate;
        out.publicSuffix = hostnameParts.slice(rulesMatch.index).join('.');
        return;
    }
    // No match found...
    // Prevailing rule is '*' so we consider the top-level domain to be the
    // public suffix of `hostname` (e.g.: 'example.org' => 'org').
    out.isIcann = false;
    out.isPrivate = false;
    out.publicSuffix = hostnameParts[hostnameParts.length - 1];
}
//# sourceMappingURL=suffix-trie.js.map