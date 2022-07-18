declare module "bel" {
    export default function (strings: TemplateStringsArray, ...keys: any[]): HTMLElement;
    export function createElement (tag: string, attributes: any, children: Array<any>): HTMLElement;
}
