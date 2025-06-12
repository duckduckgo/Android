'use strict'

const childProcess = require('child_process')
const fs = require('fs')
const os = require('os')
const path = require('path')
let SourceCodeFixer = null
try {
  SourceCodeFixer = require('eslint/lib/linter/source-code-fixer')
} catch (e) {
  SourceCodeFixer = require('eslint/lib/util/source-code-fixer')
}
const getRuleURI = require('eslint-rule-documentation')

module.exports = function (results) {
  let output = '\n'
  let errors = 0
  let warnings = 0
  const rootPath = process.cwd()

  for (const result of results) {
    const messages = result.messages

    if (messages.length === 0) {
      continue
    }

    errors += result.errorCount
    warnings += result.warningCount

    const relativePath = path.relative(rootPath, result.filePath)

    output += `${relativePath}\n`

    for (const message of messages) {
      output += `${message.line}:${message.column} ${message.ruleId || ''}`
      if (message.ruleId) {
        const ruleURI = getRuleURI(message.ruleId)
        if (ruleURI.found) {
          output += `  (${ruleURI.url})`
        }
      }
      output += `\n\t${message.message}\n`
    }

    if (messages.some(msg => msg.fix)) {
      const fixResult = SourceCodeFixer.applyFixes(result.source, messages)
      output += `\n\n$ eslint --fix ${relativePath}\n`
      output += diff(result.source, fixResult.output)
    }

    output += '\n\n'
  }

  const total = errors + warnings

  if (total > 0) {
    output += [
      '\u2716 ',
      total,
      pluralize(' problem', total),
      ' (',
      errors,
      pluralize(' error', errors),
      ', ',
      warnings,
      pluralize(' warning', warnings),
      ')\n',
    ].join('')
  }

  return total > 0 ? output : ''
}

function pluralize(word, count) {
  return count === 1 ? word : `${word}s`
}

function diff(a, b) {
  const aPath = path.join(os.tmpdir(), 'a.js')
  const bPath = path.join(os.tmpdir(), 'p.js')
  fs.writeFileSync(aPath, a, {encoding: 'utf8'})
  fs.writeFileSync(bPath, b, {encoding: 'utf8'})
  const result = childProcess.spawnSync('diff', ['-U5', aPath, bPath], {encoding: 'utf8'})
  return result.stdout.split('\n').slice(2).join('\n')
}
