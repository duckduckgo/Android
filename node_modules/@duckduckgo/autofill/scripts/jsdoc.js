const { basename } = require('path')
/**
 * @param {import("./schema").Group[]} groups
 * @returns {string}
 */
function printJs (groups) {
    let output = '// Do not edit, this was created by `scripts/schema.js`\n'
    for (let group of groups) {
        let toIndent = ''
        const linkImportText = `import("./${basename(group.input.relative)}")`
        for (let int of group.interfaces) {
            const printed = printTypeDefs(int, {linkImportText})
            toIndent += printed
            toIndent += '\n'
        }
        output += toIndent
    }
    return output
}
module.exports.printJs = printJs

/**
 * @param {import("./schema").Interface} int
 * @param {object} args
 * @param {string} args.linkImportText
 */
function printTypeDefs (int, args) {
    const {linkImportText} = args

    let output = ''
    const lines = []

    lines.push(`@link {${linkImportText}}`)
    lines.push(...printInterfaceHeading(int))
    for (let member of int.members) {
        // lines.push(memComment)
        if (member.name === '[index: string]') continue
        lines.push(...printMember(member))
    }
    output += printComments(lines.filter(Boolean))
    return output
}

/**
 * @param {import("./schema").Member} member
 * @returns {(string|undefined)[]}
 */
function printMember (member) {
    const elements = [
        `@property {${member.type}}`,
        memberName(member),
        member.title
    ]

    if (member.description?.includes('\n')) {
        return [elements.join(' '), ...member.description.split('\n')].filter(Boolean)
    } else {
        elements.push(member.description)
    }

    return [elements.filter(Boolean).join(' ')]
}

/**
 * @param {import("./schema").Member} member
 */
function memberName (member) {
    if (member.required) return member.name
    return `[${member.name}]`
}

/**
 * @param {import("./shared").Interface} int
 * @returns {string[]}
 */
function printInterfaceHeading (int) {
    let multiDesc = int.description?.includes('\n')
    if (int.members.length === 1) {
        if (int.members[0].name === '[index: string]') {
            const elements = [
                `@typedef`,
                `{Record<string, ${int.members[0].type}>}`,
                int.name,
                escape(int.title || ''),
                !multiDesc && escape(int.description || '')
            ]
            if (int.description && multiDesc) {
                return [elements.filter(Boolean).join(' '), ...int.description.split('\n')]
            }
            return [elements.filter(Boolean).join(' ')]
        }
    }
    const elements = [
        `@typedef`,
        int.members.length === 0 ? '{unknown}' : '',
        int.name,
        escape(int.title || ''),
        !multiDesc && escape(int.description || '')
    ]
    if (int.description && multiDesc) {
        return [elements.filter(Boolean).join(' '), ...int.description.split('\n')]
    }
    return [elements.filter(Boolean).join(' ')]
}

function printComments (lines) {
    let start = '/**'
    let end = ' */'
    let inner = lines.map(x => ' * ' + x)
    return [start, ...inner, end].join('\n')
}

function escape (s) {
    return s.replace(/@/, '\\@')
}
