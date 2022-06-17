import { basename } from 'path'

/**
 * @param {import("./codegen").Group[]} groups
 * @returns {string}
 */
export function printJs (groups) {
    let output = '// Do not edit, this was created by `scripts/schema.js`\n'
    for (const group of groups) {
        let toIndent = ''
        const linkImportText = `import("./${basename(group.input.relative)}")`
        for (const int of group.interfaces) {
            const printed = printTypeDefs(int, { linkImportText })
            toIndent += printed
            toIndent += '\n'
        }
        output += toIndent
    }
    return output
}

/**
 * @param {import("./codegen").Interface} int
 * @param {object} args
 * @param {string} args.linkImportText
 */
export function printTypeDefs (int, args) {
    const { linkImportText } = args

    let output = ''
    const lines = []

    lines.push(`@link {${linkImportText}}`)
    lines.push(...printInterfaceHeading(int))
    for (const member of int.members) {
        if (member.name === '[index: string]') continue
        lines.push(...printMember(member))
    }
    output += printComments(lines.filter(Boolean))
    return output
}

/**
 * @param {import("./codegen").Member} member
 * @returns {(string|undefined)[]}
 */
export function printMember (member) {
    const elements = [
        `@property {${member.type}}`,
        printMemberName(member),
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
 * @param {import("./codegen").Member} member
 */
export function printMemberName (member) {
    if (member.required) return member.name
    return `[${member.name}]`
}

/**
 * @param {import("./codegen.js").Interface} int
 * @returns {string[]}
 */
export function printInterfaceHeading (int) {
    const multiDesc = int.description?.includes('\n')
    if (int.members.length === 1) {
        if (int.members[0].name === '[index: string]') {
            const elements = [
                '@typedef',
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
        '@typedef',
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

/**
 * @param {string[]} lines
 * @returns {string}
 */
export function printComments (lines) {
    const start = '/**'
    const end = ' */'
    const inner = lines.map(x => ' * ' + x)
    return [start, ...inner, end].join('\n')
}

function escape (s) {
    return s.replace(/@/, '\\@')
}
