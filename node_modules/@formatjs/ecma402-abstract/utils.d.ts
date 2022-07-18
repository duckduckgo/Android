/**
 * Cannot do Math.log(x) / Math.log(10) bc if IEEE floating point issue
 * @param x number
 */
export declare function getMagnitude(x: number): number;
export declare function repeat(s: string, times: number): string;
export declare function setInternalSlot<Instance extends object, Internal extends object, Field extends keyof Internal>(map: WeakMap<Instance, Internal>, pl: Instance, field: Field, value: NonNullable<Internal>[Field]): void;
export declare function setMultiInternalSlots<Instance extends object, Internal extends object, K extends keyof Internal>(map: WeakMap<Instance, Internal>, pl: Instance, props: Pick<NonNullable<Internal>, K>): void;
export declare function getInternalSlot<Instance extends object, Internal extends object, Field extends keyof Internal>(map: WeakMap<Instance, Internal>, pl: Instance, field: Field): Internal[Field];
export declare function getMultiInternalSlots<Instance extends object, Internal extends object, Field extends keyof Internal>(map: WeakMap<Instance, Internal>, pl: Instance, ...fields: Field[]): Pick<Internal, Field>;
export interface LiteralPart {
    type: 'literal';
    value: string;
}
export declare function isLiteralPart(patternPart: LiteralPart | {
    type: string;
    value?: string;
}): patternPart is LiteralPart;
export declare function defineProperty<T extends object>(target: T, name: string | symbol, { value }: {
    value: any;
} & ThisType<any>): void;
export declare const UNICODE_EXTENSION_SEQUENCE_REGEX: RegExp;
export declare function invariant(condition: boolean, message: string, Err?: any): asserts condition;
//# sourceMappingURL=utils.d.ts.map