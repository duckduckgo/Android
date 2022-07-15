import { PartitionNumberPattern } from './PartitionNumberPattern';
import { ArrayCreate } from '../262';
export function FormatNumericToParts(nf, x, implDetails) {
    var parts = PartitionNumberPattern(nf, x, implDetails);
    var result = ArrayCreate(0);
    for (var _i = 0, parts_1 = parts; _i < parts_1.length; _i++) {
        var part = parts_1[_i];
        result.push({
            type: part.type,
            value: part.value,
        });
    }
    return result;
}
