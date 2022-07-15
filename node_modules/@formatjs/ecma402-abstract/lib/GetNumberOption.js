/**
 * https://tc39.es/ecma402/#sec-getnumberoption
 * @param options
 * @param property
 * @param min
 * @param max
 * @param fallback
 */
import { DefaultNumberOption } from './DefaultNumberOption';
export function GetNumberOption(options, property, minimum, maximum, fallback) {
    var val = options[property];
    // @ts-expect-error
    return DefaultNumberOption(val, minimum, maximum, fallback);
}
