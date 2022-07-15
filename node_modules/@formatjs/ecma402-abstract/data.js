"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.isMissingLocaleDataError = void 0;
var tslib_1 = require("tslib");
var MissingLocaleDataError = /** @class */ (function (_super) {
    (0, tslib_1.__extends)(MissingLocaleDataError, _super);
    function MissingLocaleDataError() {
        var _this = _super !== null && _super.apply(this, arguments) || this;
        _this.type = 'MISSING_LOCALE_DATA';
        return _this;
    }
    return MissingLocaleDataError;
}(Error));
function isMissingLocaleDataError(e) {
    return e.type === 'MISSING_LOCALE_DATA';
}
exports.isMissingLocaleDataError = isMissingLocaleDataError;
