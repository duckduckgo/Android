/**
 * @license
 * Copyright 2021 Google Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
import { __assign, __extends } from "tslib";
import { MDCComponent } from '@material/base/component';
import { MDCRipple } from '@material/ripple/component';
import { MDCRippleFoundation } from '@material/ripple/foundation';
import { Selectors } from './constants';
import { MDCSwitchRenderFoundation } from './foundation';
/**
 * `MDCSwitch` provides a component implementation of a Material Design switch.
 */
var MDCSwitch = /** @class */ (function (_super) {
    __extends(MDCSwitch, _super);
    function MDCSwitch(root, foundation) {
        var _this = _super.call(this, root, foundation) || this;
        _this.root = root;
        return _this;
    }
    /**
     * Creates a new `MDCSwitch` and attaches it to the given root element.
     * @param root The root to attach to.
     * @return the new component instance.
     */
    MDCSwitch.attachTo = function (root) {
        return new MDCSwitch(root);
    };
    MDCSwitch.prototype.initialize = function () {
        this.ripple = new MDCRipple(this.root, this.createRippleFoundation());
    };
    MDCSwitch.prototype.initialSyncWithDOM = function () {
        var rippleElement = this.root.querySelector(Selectors.RIPPLE);
        if (!rippleElement) {
            throw new Error("Switch " + Selectors.RIPPLE + " element is required.");
        }
        this.rippleElement = rippleElement;
        this.root.addEventListener('click', this.foundation.handleClick);
        this.foundation.initFromDOM();
    };
    MDCSwitch.prototype.destroy = function () {
        _super.prototype.destroy.call(this);
        this.ripple.destroy();
        this.root.removeEventListener('click', this.foundation.handleClick);
    };
    MDCSwitch.prototype.getDefaultFoundation = function () {
        return new MDCSwitchRenderFoundation(this.createAdapter());
    };
    MDCSwitch.prototype.createAdapter = function () {
        var _this = this;
        return {
            addClass: function (className) {
                _this.root.classList.add(className);
            },
            hasClass: function (className) { return _this.root.classList.contains(className); },
            isDisabled: function () { return _this.root.disabled; },
            removeClass: function (className) {
                _this.root.classList.remove(className);
            },
            setAriaChecked: function (ariaChecked) {
                return _this.root.setAttribute('aria-checked', ariaChecked);
            },
            setDisabled: function (disabled) {
                _this.root.disabled = disabled;
            },
            state: this,
        };
    };
    MDCSwitch.prototype.createRippleFoundation = function () {
        return new MDCRippleFoundation(this.createRippleAdapter());
    };
    MDCSwitch.prototype.createRippleAdapter = function () {
        var _this = this;
        return __assign(__assign({}, MDCRipple.createAdapter(this)), { computeBoundingRect: function () { return _this.rippleElement.getBoundingClientRect(); }, isUnbounded: function () { return true; } });
    };
    return MDCSwitch;
}(MDCComponent));
export { MDCSwitch };
//# sourceMappingURL=component.js.map