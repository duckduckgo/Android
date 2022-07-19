declare type Func = (...args: any[]) => any;
export interface Cache<K, V> {
    create: CacheCreateFunc<K, V>;
}
interface CacheCreateFunc<K, V> {
    (): DefaultCache<K, V>;
}
interface DefaultCache<K, V> {
    get(key: K): V;
    set(key: K, value: V): void;
}
export declare type Serializer = (args: any[]) => string;
export interface Options<F extends Func> {
    cache?: Cache<string, ReturnType<F>>;
    serializer?: Serializer;
    strategy?: MemoizeFunc<F>;
}
export interface ResolvedOptions<F extends Func> {
    cache: Cache<string, ReturnType<F>>;
    serializer: Serializer;
}
export interface MemoizeFunc<F extends Func> {
    (fn: F, options?: Options<F>): F;
}
export default function memoize<F extends Func>(fn: F, options?: Options<F>): F | ((arg: any) => any);
export declare type StrategyFn = <F extends Func>(this: unknown, fn: F, cache: DefaultCache<string, ReturnType<F>>, serializer: Serializer, arg: any) => any;
export interface Strategies<F extends Func> {
    variadic: MemoizeFunc<F>;
    monadic: MemoizeFunc<F>;
}
export declare const strategies: Strategies<Func>;
export {};
//# sourceMappingURL=index.d.ts.map