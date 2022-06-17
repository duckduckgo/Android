const {basename} = require('path')

/**
 * @typedef {import("./schema").Interface} Interface
 * @typedef {import("./schema").Group} Group
 * @typedef {import("./schema").Member} Member
 */

/**
 * @param {string[]} members
 * @returns {string|*}
 */
function formatUnionMembers (members) {
    if (members.length === 0) return 'unknown'
    return members.join(' | ')
}

/**
 * @param {string[]} members
 * @returns {string}
 */
function formatArrayMembers (members) {
    if (members.length === 0) return 'any[]'
    if (members.length === 1) return `${members[0]}[]`
    return `(${members.join('|')})[]`
}

function intComment (int, linkImportText) {
    let commentLines = []
    if (int.title) {
        commentLines.push(int.title)
    }
    if (int.description) {
        if (commentLines.length) commentLines.push('')
        commentLines.push(...int.description.split('\n'))
    }
    if (commentLines.length) commentLines.push('')
    commentLines.push(`@link {${linkImportText}}`)
    const printedComment = commentLines.length > 0 ? printComments(commentLines) + '\n' : '\n'
    return printedComment
}

function memberComment (mem) {
    let commentLines = []
    if (mem.title) {
        commentLines.push(mem.title)
    }
    if (mem.description) {
        commentLines.push(mem.description)
    }
    const printedComment = commentLines.length > 0 ? printComments(commentLines) + '\n' : ''
    return printedComment
}

/**
 * @param {Interface} int
 * @param {object} args
 * @param {string} args.linkImportText
 */
function printInterface (int, args) {
    const {linkImportText} = args

    const printedComment = intComment(int, linkImportText)
    let output = ''
    output += printedComment
    output += `interface ${int.name} {\n`
    for (let member of int.members) {
        const memComment = memberComment(member)
        output += indent(memComment)
        output += indent(printMember(member), '') + `\n`
    }
    output += `}`
    return output
}

function printMember (member) {
    return `${member.name}${member.required ? '' : '?'}: ${member.type.join('|')}`
}

function indent (source, indent = '    ') {
    return (source || '').split('\n').map(x => indent + x).join('\n')
}

function printComments (lines) {
    return `
/**
${lines.map(x => ' * ' + x).join('\n')}
 */`
}

/**
 * @typedef {object} NamedEntry
 * @property {Group|null} request
 * @property {Group|null} response
 * @property {Group[]} other
 * @property {string} name
 */

/**
 * @typedef {Record<string, NamedEntry>} Grouped
 */

/**
 * @param {Group[]} groups
 * @returns {Grouped}
 */
function grouped (groups) {
    /** @type {Record<string, NamedEntry>} */
    let named = {}
    for (let group of groups) {
        let file = basename(group.input.relative)
        let [type, name] = file.split(/\./g)
        if (type === 'request' || type === 'response') {
            if (!named[name]) named[name] = {request: null, response: null, other: [], name}
            named[name][type] = group
        } else {
            if (!named[type]) named[type] = {request: null, response: null, other: [], name: type}
            named[type].other.push(group)
        }
    }
    return named
}

/**
 * @param {Grouped} grouped
 */
function printGroupsMd (grouped) {
    const ordered = orderGroups(grouped)
    const blocks = []
    for (let orderedElement of ordered) {
        const inner = []
        const group = grouped[orderedElement]
        inner.push(blockTitle(group))
        if (group.request) {
            inner.push(requestTitle(group.request))
            inner.push(detailsSummary(group.request))
            for (let int of group.request.interfaces) {
                inner.push(tsInterfaceDesc(int))
                inner.push(tsCodeFence(printInterface(int, {linkImportText: `./${basename(group.request.input.relative)}`})))
            }
        }
        if (group.response) {
            inner.push(responseTitle(group.response))
            inner.push(detailsSummary(group.response))
            for (let int of group.response.interfaces) {
                inner.push(tsInterfaceDesc(int))
                inner.push(tsCodeFence(printInterface(int, {linkImportText: `./${basename(group.response.input.relative)}`})))
            }
        }
        for (let otherElement of group.other) {
            inner.push(detailsSummary(otherElement))
            for (let int of otherElement.interfaces) {
                inner.push(tsInterfaceDesc(int))
                inner.push(tsCodeFence(printInterface(int, {linkImportText: `./${basename(otherElement.input.relative)}`})))
            }
        }
        blocks.push(inner.join('\n\n'))
    }
    return blocks.join('\n\n---\n')
}

/** @param {NamedEntry} entry */
function blockTitle (entry) {
    return `## \`${entry.name}\``
}

/** @param {Group} _group */
function requestTitle (_group) {
    return `**request**`
}

/** @param {Group} _group */
function responseTitle (_group) {
    return `**response**`
}

/**
 * @param {Group} group
 */
function detailsSummary (group) {
    return `
<details>
<summary><code>${basename(group.input.relative)}</code></summary>
<br/>

[./${basename(group.input.relative)}](./${basename(group.input.relative)})

\`\`\`json
${JSON.stringify(group.input.json, null, 2)}
\`\`\`

</details>
  `
}

/**
 * @param {string} input
 */
function tsCodeFence (input) {
    return `

\`\`\`ts
${input}
\`\`\`
`
}

/**
 * @param {import('./schema').Interface} int
 */
function tsInterfaceDesc (int) {
    const lines = []
    lines.push(`### ` + int.name)
    // lines.push(int.title);
    // lines.push('');
    // lines.push(int.description);
    return lines.join('\n')
}

/**
 * @param {Grouped} grouped
 * @return {(keyof Grouped)[]}
 */
function orderGroups (grouped) {
    return Object.keys(grouped)
        .sort((a, b) => {
            let _a = 0
            let _b = 0
            if (grouped[a].request) _a += 1
            if (grouped[a].response) _a += 1

            if (grouped[b].request) _b += 1
            if (grouped[b].response) _b += 1
            return _b - _a
        })
}

module.exports = {
    printGroupsMd,
    formatArrayMembers,
    formatUnionMembers,
    grouped,
    indent
}
