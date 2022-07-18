import { __spreadArray } from "tslib";
import { isPluralElement, isSelectElement, } from './types';
function cloneDeep(obj) {
    if (Array.isArray(obj)) {
        // @ts-expect-error meh
        return __spreadArray([], obj.map(cloneDeep), true);
    }
    if (typeof obj === 'object') {
        // @ts-expect-error meh
        return Object.keys(obj).reduce(function (cloned, k) {
            // @ts-expect-error meh
            cloned[k] = cloneDeep(obj[k]);
            return cloned;
        }, {});
    }
    return obj;
}
/**
 * Hoist all selectors to the beginning of the AST & flatten the
 * resulting options. E.g:
 * "I have {count, plural, one{a dog} other{many dogs}}"
 * becomes "{count, plural, one{I have a dog} other{I have many dogs}}".
 * If there are multiple selectors, the order of which one is hoisted 1st
 * is non-deterministic.
 * The goal is to provide as many full sentences as possible since fragmented
 * sentences are not translator-friendly
 * @param ast AST
 */
export function hoistSelectors(ast) {
    var _loop_1 = function (i) {
        var el = ast[i];
        if (isPluralElement(el) || isSelectElement(el)) {
            // pull this out of the ast and move it to the top
            var cloned = cloneDeep(el);
            var options_1 = cloned.options;
            cloned.options = Object.keys(options_1).reduce(function (all, k) {
                var newValue = hoistSelectors(__spreadArray(__spreadArray(__spreadArray([], ast.slice(0, i), true), options_1[k].value, true), ast.slice(i + 1), true));
                all[k] = {
                    value: newValue,
                };
                return all;
            }, {});
            return { value: [cloned] };
        }
    };
    for (var i = 0; i < ast.length; i++) {
        var state_1 = _loop_1(i);
        if (typeof state_1 === "object")
            return state_1.value;
    }
    return ast;
}
