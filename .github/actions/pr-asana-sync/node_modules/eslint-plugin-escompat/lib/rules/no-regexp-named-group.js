'use strict';

const hasNamedGroup = s => /\(\?<[_$\w]/.test(s)

module.exports = (context, badBrowser) => ({
  'Literal[regex]'(node) {
    if (hasNamedGroup(node.regex.pattern)) {
      context.report(node, `RegExp named groups are not supported in ${badBrowser}`)
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
          hasNamedGroup(source.value)
        ) ||
        (
          source.type === 'TemplateLiteral' &&
          source.quasis.some(({value: {raw}}) => hasNamedGroup(raw))
        )
      )
    ) {
      context.report(node, `RegExp named groups are not supported in ${badBrowser}`)
    }
  }
})
