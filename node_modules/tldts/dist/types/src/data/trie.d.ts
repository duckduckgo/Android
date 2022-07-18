export interface ITrie {
    $: number;
    succ: {
        [label: string]: ITrie;
    };
}
export declare const exceptions: ITrie;
export declare const rules: ITrie;
