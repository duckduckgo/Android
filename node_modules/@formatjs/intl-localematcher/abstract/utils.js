"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.invariant = exports.UNICODE_EXTENSION_SEQUENCE_REGEX = void 0;
exports.UNICODE_EXTENSION_SEQUENCE_REGEX = /-u(?:-[0-9a-z]{2,8})+/gi;
function invariant(condition, message, Err) {
    if (Err === void 0) { Err = Error; }
    if (!condition) {
        throw new Err(message);
    }
}
exports.invariant = invariant;
