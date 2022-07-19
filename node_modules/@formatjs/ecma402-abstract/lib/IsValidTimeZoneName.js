/**
 * https://tc39.es/ecma402/#sec-isvalidtimezonename
 * @param tz
 * @param implDetails implementation details
 */
export function IsValidTimeZoneName(tz, _a) {
    var tzData = _a.tzData, uppercaseLinks = _a.uppercaseLinks;
    var uppercasedTz = tz.toUpperCase();
    var zoneNames = new Set();
    var linkNames = new Set();
    Object.keys(tzData)
        .map(function (z) { return z.toUpperCase(); })
        .forEach(function (z) { return zoneNames.add(z); });
    Object.keys(uppercaseLinks).forEach(function (linkName) {
        linkNames.add(linkName.toUpperCase());
        zoneNames.add(uppercaseLinks[linkName].toUpperCase());
    });
    return zoneNames.has(uppercasedTz) || linkNames.has(uppercasedTz);
}
