/**
 * This takes any *.zod.js file and replaces all of the `export const` lines
 * with null exports.
 *
 * Eg:
 *   input: `export const mySchema = z.string();`
 *   output: `export const mySchema = null`
 *
 * @param {string} fileAsString
 * @return {string}
 */
function replaceConstExports (fileAsString) {
    // Break up the incoming file into lines that contain const exports.
    const asLines = fileAsString
        .split('\n')
        .filter(x => x.startsWith('export const'))

    // now convert `export const x = z...` into `export const x = null`
    const asNames = asLines.map(x => {
        const [, , ident] = x.split(' ')
        return `export const ` + ident + ` = null;`
    }).flat()

    return asNames.join('\n')
}

module.exports.replaceConstExports = replaceConstExports
