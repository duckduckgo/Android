"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.renderMD = void 0;
// Setup markdown with limited tag support because Asana limits us
const markdown_it_1 = __importDefault(require("markdown-it"));
const md = (0, markdown_it_1.default)('zero', { linkify: true }).enable([
    'table',
    'heading',
    'fence',
    'block',
    'backticks',
    'code',
    'linkify',
    'link',
    'list',
    'emphasis',
    'strikethrough'
]);
// Asana doesn't let us use <p></p> so instead let's just add a newline at the end
// eslint-disable-next-line camelcase
md.renderer.rules.paragraph_open = function () {
    return '';
};
// eslint-disable-next-line camelcase
md.renderer.rules.paragraph_close = function () {
    return '\n\n';
};
// Table support fixes for Asana
// eslint-disable-next-line camelcase
md.renderer.rules.thead_open = function () {
    return '';
};
// eslint-disable-next-line camelcase
md.renderer.rules.thead_close = function () {
    return '';
};
// eslint-disable-next-line camelcase
md.renderer.rules.th_open = function () {
    return '<td><strong>';
};
// eslint-disable-next-line camelcase
md.renderer.rules.th_close = function () {
    return '</strong></td>';
};
// eslint-disable-next-line camelcase
md.renderer.rules.tbody_open = function () {
    return '';
};
// eslint-disable-next-line camelcase
md.renderer.rules.tbody_close = function () {
    return '';
};
function renderMD(text) {
    // Not aware of a way to fix these with renderer rules so we tweak manually
    return md
        .render(text)
        .replace(/<pre>/gm, '')
        .replace(/<\/pre>/gm, '')
        .replace(/(<li>.*)\n/gm, '$1');
}
exports.renderMD = renderMD;
