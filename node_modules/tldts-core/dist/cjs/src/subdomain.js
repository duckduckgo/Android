"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/**
 * Returns the subdomain of a hostname string
 */
function getSubdomain(hostname, domain) {
    // If `hostname` and `domain` are the same, then there is no sub-domain
    if (domain.length === hostname.length) {
        return '';
    }
    return hostname.slice(0, -domain.length - 1);
}
exports.default = getSubdomain;
//# sourceMappingURL=subdomain.js.map