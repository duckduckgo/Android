'use strict';

const hasLookbehind = s => s.includes('(?<=') || s.includes('(?<!')

module.exports = (context, badBrowser) => ({
  'Literal[regex]'(node) {
    if (hasLookbehind(node.regex.pattern)) {
      context.report(node, `RegExp lookbehinds are not supported in ${badBrowser}`)
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
          hasLookbehind(source.value)
        ) ||
        (
          source.type === 'TemplateLiteral' &&
          source.quasis.some(({value: {raw}}) => hasLookbehind(raw))
        )
      )
    ) {
      context.report(node, `RegExp lookbehinds are not supported in ${badBrowser}`)
    }
  }
})
