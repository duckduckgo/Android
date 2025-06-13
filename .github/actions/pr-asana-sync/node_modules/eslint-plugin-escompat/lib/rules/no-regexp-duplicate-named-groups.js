'use strict';

const hasDuplicateNamedGroups = s => /(\(\?<[_$\w]*?)>.*?\1>/.test(s)

module.exports = (context, badBrowser) => ({
  'Literal[regex]'(node) {
    if (hasDuplicateNamedGroups(node.regex.pattern)) {
      context.report(node, `RegExp duplicate named groups are not supported in ${badBrowser}`)
    }
  },
  'CallExpression[callee.name="RegExp"], NewExpression[callee.name="RegExp"]'(node) {
    const [source] = node.arguments;
    if (
      source &&
      (
        (
          source.type === 'Literal' &&
          typeof source.value === 'string' &&
          hasDuplicateNamedGroups(source.value)
        ) ||
        (
          source.type === 'TemplateLiteral' &&
          source.quasis.some(({value: {raw}}) => hasDuplicateNamedGroups(raw))
        )
      )
    ) {
      context.report(node, `RegExp duplicate named groups are not supported in ${badBrowser}`)
    }
  }
})
