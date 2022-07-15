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
import { MDCObserverFoundation } from '@material/base/observer-foundation';
import { MDCSwitchAdapter, MDCSwitchRenderAdapter } from './adapter';
/**
 * `MDCSwitchFoundation` provides a state-only foundation for a switch
 * component.
 *
 * State observers and event handler entrypoints update a component's adapter's
 * state with the logic needed for switch to function.
 */
export declare class MDCSwitchFoundation extends MDCObserverFoundation<MDCSwitchAdapter> {
    constructor(adapter: MDCSwitchAdapter);
    /**
     * Initializes the foundation and starts observing state changes.
     */
    init(): void;
    /**
     * Event handler for switch click events. Clicking on a switch will toggle its
     * selected state.
     */
    handleClick(): void;
    protected stopProcessingIfDisabled(): void;
}
/**
 * `MDCSwitchRenderFoundation` provides a state and rendering foundation for a
 * switch component.
 *
 * State observers and event handler entrypoints update a component's
 * adapter's state with the logic needed for switch to function.
 *
 * In response to state changes, the rendering foundation uses the component's
 * render adapter to keep the component's DOM updated with the state.
 */
export declare class MDCSwitchRenderFoundation extends MDCSwitchFoundation {
    protected adapter: MDCSwitchRenderAdapter;
    /**
     * Initializes the foundation and starts observing state changes.
     */
    init(): void;
    /**
     * Initializes the foundation from a server side rendered (SSR) component.
     * This will sync the adapter's state with the current state of the DOM.
     *
     * This method should be called after `init()`.
     */
    initFromDOM(): void;
    protected onDisabledChange(): void;
    protected onProcessingChange(): void;
    protected onSelectedChange(): void;
    private toggleClass;
}
