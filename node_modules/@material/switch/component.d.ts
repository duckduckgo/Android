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
import { MDCComponent } from '@material/base/component';
import { MDCRippleAdapter } from '@material/ripple/adapter';
import { MDCRipple } from '@material/ripple/component';
import { MDCRippleFoundation } from '@material/ripple/foundation';
import { MDCRippleCapableSurface } from '@material/ripple/types';
import { MDCSwitchRenderAdapter, MDCSwitchState } from './adapter';
import { MDCSwitchRenderFoundation } from './foundation';
/**
 * `MDCSwitch` provides a component implementation of a Material Design switch.
 */
export declare class MDCSwitch extends MDCComponent<MDCSwitchRenderFoundation> implements MDCSwitchState, MDCRippleCapableSurface {
    root: HTMLButtonElement;
    /**
     * Creates a new `MDCSwitch` and attaches it to the given root element.
     * @param root The root to attach to.
     * @return the new component instance.
     */
    static attachTo(root: HTMLButtonElement): MDCSwitch;
    disabled: boolean;
    processing: boolean;
    selected: boolean;
    ripple: MDCRipple;
    private rippleElement;
    constructor(root: HTMLButtonElement, foundation?: MDCSwitchRenderFoundation);
    initialize(): void;
    initialSyncWithDOM(): void;
    destroy(): void;
    getDefaultFoundation(): MDCSwitchRenderFoundation;
    protected createAdapter(): MDCSwitchRenderAdapter;
    protected createRippleFoundation(): MDCRippleFoundation;
    protected createRippleAdapter(): MDCRippleAdapter;
}
