/**
 * @license
 * Copyright 2018 Google Inc.
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
import { __assign, __extends, __read, __spreadArray } from "tslib";
import { MDCComponent } from '@material/base/component';
import { applyPassive } from '@material/dom/events';
import { matches } from '@material/dom/ponyfill';
import { MDCRipple } from '@material/ripple/component';
import { MDCRippleFoundation } from '@material/ripple/foundation';
import { MDCSwitchFoundation } from './foundation';
var MDCSwitch = /** @class */ (function (_super) {
    __extends(MDCSwitch, _super);
    function MDCSwitch() {
        var _this = _super !== null && _super.apply(this, arguments) || this;
        _this.rippleSurface = _this.createRipple();
        return _this;
    }
    MDCSwitch.attachTo = function (root) {
        return new MDCSwitch(root);
    };
    MDCSwitch.prototype.destroy = function () {
        _super.prototype.destroy.call(this);
        this.rippleSurface.destroy();
        this.nativeControl.removeEventListener('change', this.changeHandler);
    };
    MDCSwitch.prototype.initialSyncWithDOM = function () {
        var _this = this;
        this.changeHandler = function () {
            var _a;
            var args = [];
            for (var _i = 0; _i < arguments.length; _i++) {
                args[_i] = arguments[_i];
            }
            (_a = _this.foundation).handleChange.apply(_a, __spreadArray([], __read(args)));
        };
        this.nativeControl.addEventListener('change', this.changeHandler);
        // Sometimes the checked state of the input element is saved in the history.
        // The switch styling should match the checked state of the input element.
        // Do an initial sync between the native control and the foundation.
        this.checked = this.checked;
    };
    MDCSwitch.prototype.getDefaultFoundation = function () {
        var _this = this;
        // DO NOT INLINE this variable. For backward compatibility, foundations take a Partial<MDCFooAdapter>.
        // To ensure we don't accidentally omit any methods, we need a separate, strongly typed adapter variable.
        var adapter = {
            addClass: function (className) { return _this.root.classList.add(className); },
            removeClass: function (className) { return _this.root.classList.remove(className); },
            setNativeControlChecked: function (checked) { return _this.nativeControl.checked =
                checked; },
            setNativeControlDisabled: function (disabled) { return _this.nativeControl.disabled =
                disabled; },
            setNativeControlAttr: function (attr, value) {
                _this.nativeControl.setAttribute(attr, value);
            },
        };
        return new MDCSwitchFoundation(adapter);
    };
    Object.defineProperty(MDCSwitch.prototype, "ripple", {
        get: function () {
            return this.rippleSurface;
        },
        enumerable: false,
        configurable: true
    });
    Object.defineProperty(MDCSwitch.prototype, "checked", {
        get: function () {
            return this.nativeControl.checked;
        },
        set: function (checked) {
            this.foundation.setChecked(checked);
        },
        enumerable: false,
        configurable: true
    });
    Object.defineProperty(MDCSwitch.prototype, "disabled", {
        get: function () {
            return this.nativeControl.disabled;
        },
        set: function (disabled) {
            this.foundation.setDisabled(disabled);
        },
        enumerable: false,
        configurable: true
    });
    MDCSwitch.prototype.createRipple = function () {
        var _this = this;
        var RIPPLE_SURFACE_SELECTOR = MDCSwitchFoundation.strings.RIPPLE_SURFACE_SELECTOR;
        var rippleSurface = this.root.querySelector(RIPPLE_SURFACE_SELECTOR);
        // DO NOT INLINE this variable. For backward compatibility, foundations take a Partial<MDCFooAdapter>.
        // To ensure we don't accidentally omit any methods, we need a separate, strongly typed adapter variable.
        var adapter = __assign(__assign({}, MDCRipple.createAdapter(this)), { addClass: function (className) { return rippleSurface.classList.add(className); }, computeBoundingRect: function () { return rippleSurface.getBoundingClientRect(); }, deregisterInteractionHandler: function (evtType, handler) {
                _this.nativeControl.removeEventListener(evtType, handler, applyPassive());
            }, isSurfaceActive: function () { return matches(_this.nativeControl, ':active'); }, isUnbounded: function () { return true; }, registerInteractionHandler: function (evtType, handler) {
                _this.nativeControl.addEventListener(evtType, handler, applyPassive());
            }, removeClass: function (className) {
                rippleSurface.classList.remove(className);
            }, updateCssVariable: function (varName, value) {
                rippleSurface.style.setProperty(varName, value);
            } });
        return new MDCRipple(this.root, new MDCRippleFoundation(adapter));
    };
    Object.defineProperty(MDCSwitch.prototype, "nativeControl", {
        get: function () {
            var NATIVE_CONTROL_SELECTOR = MDCSwitchFoundation.strings.NATIVE_CONTROL_SELECTOR;
            return this.root.querySelector(NATIVE_CONTROL_SELECTOR);
        },
        enumerable: false,
        configurable: true
    });
    return MDCSwitch;
}(MDCComponent));
export { MDCSwitch };
//# sourceMappingURL=component.js.map