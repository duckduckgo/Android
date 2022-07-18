import { __spreadArray } from "tslib";
import { isLiteralElement, isTagElement, isSelectElement, isArgumentElement, isDateElement, isTimeElement, isNumberElement, isPluralElement, TYPE, SKELETON_TYPE, isPoundElement, } from './types';
export function printAST(ast) {
    return doPrintAST(ast, false);
}
export function doPrintAST(ast, isInPlural) {
    var printedNodes = ast.map(function (el) {
        if (isLiteralElement(el)) {
            return printLiteralElement(el, isInPlural);
        }
        if (isArgumentElement(el)) {
            return printArgumentElement(el);
        }
        if (isDateElement(el) || isTimeElement(el) || isNumberElement(el)) {
            return printSimpleFormatElement(el);
        }
        if (isPluralElement(el)) {
            return printPluralElement(el);
        }
        if (isSelectElement(el)) {
            return printSelectElement(el);
        }
        if (isPoundElement(el)) {
            return '#';
        }
        if (isTagElement(el)) {
            return printTagElement(el);
        }
    });
    return printedNodes.join('');
}
function printTagElement(el) {
    return "<".concat(el.value, ">").concat(printAST(el.children), "</").concat(el.value, ">");
}
function printEscapedMessage(message) {
    return message.replace(/([{}](?:.*[{}])?)/su, "'$1'");
}
function printLiteralElement(_a, isInPlural) {
    var value = _a.value;
    var escaped = printEscapedMessage(value);
    return isInPlural ? escaped.replace('#', "'#'") : escaped;
}
function printArgumentElement(_a) {
    var value = _a.value;
    return "{".concat(value, "}");
}
function printSimpleFormatElement(el) {
    return "{".concat(el.value, ", ").concat(TYPE[el.type]).concat(el.style ? ", ".concat(printArgumentStyle(el.style)) : '', "}");
}
function printNumberSkeletonToken(token) {
    var stem = token.stem, options = token.options;
    return options.length === 0
        ? stem
        : "".concat(stem).concat(options.map(function (o) { return "/".concat(o); }).join(''));
}
function printArgumentStyle(style) {
    if (typeof style === 'string') {
        return printEscapedMessage(style);
    }
    else if (style.type === SKELETON_TYPE.dateTime) {
        return "::".concat(printDateTimeSkeleton(style));
    }
    else {
        return "::".concat(style.tokens.map(printNumberSkeletonToken).join(' '));
    }
}
export function printDateTimeSkeleton(style) {
    return style.pattern;
}
function printSelectElement(el) {
    var msg = [
        el.value,
        'select',
        Object.keys(el.options)
            .map(function (id) { return "".concat(id, "{").concat(doPrintAST(el.options[id].value, false), "}"); })
            .join(' '),
    ].join(',');
    return "{".concat(msg, "}");
}
function printPluralElement(el) {
    var type = el.pluralType === 'cardinal' ? 'plural' : 'selectordinal';
    var msg = [
        el.value,
        type,
        __spreadArray([
            el.offset ? "offset:".concat(el.offset) : ''
        ], Object.keys(el.options).map(function (id) { return "".concat(id, "{").concat(doPrintAST(el.options[id].value, true), "}"); }), true).filter(Boolean)
            .join(' '),
    ].join(',');
    return "{".concat(msg, "}");
}
