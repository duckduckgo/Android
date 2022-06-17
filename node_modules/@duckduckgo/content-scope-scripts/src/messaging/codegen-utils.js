/**
 * @param {string[]} members
 * @returns {string|*}
 */
export function formatUnionMembers (members) {
    if (members.length === 0) return 'unknown'
    return members.join(' | ')
}

/**
 * @param {string[]} members
 * @returns {string}
 */
export function formatArrayMembers (members) {
    if (members.length === 0) return 'any[]'
    if (members.length === 1) return `${members[0]}[]`
    return `(${members.join('|')})[]`
}
