/**
 *
 * This file is used to generate the table of inputs inside `index.html`
 *
 * You can run this any time that `../rules.json` has changed, or after
 * adding any manual entries to `manualEntries` below.
 *
 */
const {readFileSync, writeFileSync} = require('fs')
const {join} = require('path')
const rules = require('../rules.json')
const filePath = join(__dirname, 'index.html')
const html = readFileSync(filePath, 'utf8')
const {Password} = require('../lib/apple.password')

let s = ''

const manualEntries = {
    // this is just to test the use of chars that need escaping
    '" test': { 'password-rules': `minlength: 6; required: lower, upper; required: digit; required: ["]` }
}

const joined = [...Object.entries(manualEntries), ...Object.entries(rules)]
const outputs = []
const password = new Password({
    getRandomValues: (/** @type {any} */ v) => require('crypto').randomFillSync(v)
})

for (let [domain, value] of joined) {
    const rulesString = value['password-rules']
    if (domain && rulesString) {
        const {parameters, generate, entropy} = password.parse(rulesString)
        let charsetLength = parameters.PasswordAllowedCharacters.length
        let passwords = new Array(5).fill(0).map((_, i) => i)
            .map(() => {
                const pw = generate()
                return { pw, length: pw.length }
            })
        const averageLength = passwords.reduce((acc, a) => acc + a.length, 0) / 5

        outputs.push({
            domain,
            rules: rulesString,
            charsetLength,
            averageLength,
            entropy,
            passwords,
            charset: parameters.PasswordAllowedCharacters
        })
    }
}

outputs.sort((a, b) => a.entropy - b.entropy)

for (let output of outputs) {
    const {averageLength, entropy, passwords, charsetLength, rules, domain, charset} = output
    let entropyScore = 'Very Strong'
    if (entropy >= 60 && entropy <= 127) {
        entropyScore = 'Strong'
    } else if (entropy >= 36 && entropy < 60) {
        entropyScore = 'Reasonable'
    } else if (entropy >= 28 && entropy < 36) {
        entropyScore = 'Weak'
    } else if (entropy < 28) {
        entropyScore = 'Very Weak'
    }
    s += `
<tr>
    <td>
        <button type="button" data-pw="${escapeXML(rules)}">${domain}</button>
    </td>
    <td>
        <pre><code><b>Rules: </b><span>${escapeXML(rules)}</span></code></pre>
        <pre><code><b>charset</b>: ${escapeXML(charset)}</code></pre>
        <pre><code><b>charset size</b>: ${charsetLength}, <b>length:</b> ${averageLength}</code></pre>
        <pre><code><b>entropy:</b> <span class="rules" data-entropy="${entropyScore}">${entropyScore}</span> ${entropy.toFixed(2)}</code></pre>
        <div><pre><code>${escapeXML(passwords.map(pw => `${pw.pw} (${pw.length})`).join('\n'))}</code></pre></div>
    </td>
</tr>
        `
}

const markerStart = '<table id="table">'
const start = html.indexOf(markerStart) + markerStart.length
const end = html.indexOf('</table>')

const newHtml = html.slice(0, start) + s + html.slice(end)

writeFileSync(filePath, newHtml)

/**
 * Escapes any occurrences of &, ", <, > or / with XML entities.
 * @param {string} str The string to escape.
 * @return {string} The escaped string.
 */
function escapeXML (str) {
    const replacements = { '&': '&amp;', '"': '&quot;', "'": '&apos;', '<': '&lt;', '>': '&gt;', '/': '&#x2F;' }
    return String(str).replace(/[&"'<>/]/g, m => replacements[m])
}
