'use strict';

module.exports = (context, badBrowser) => ({
  'Literal[regex]'(node) {
    if (node.regex.flags.includes('v')) {
      context.report(node, `RegExp "v" flag is not supported in ${badBrowser}`)
    }
  },
  'CallExpression[callee.name="RegExp"], NewExpression[callee.name="RegExp"]'(node) {
    const [, flags] = node.arguments;
    if (
      flags &&
      (
        (
          flags.type === 'Literal' &&
          typeof flags.value === 'string' &&
          flags.value.includes('v')
        ) ||
        (
          flags.type === 'TemplateLiteral' &&
          flags.quasis.some(({value: {raw}}) => raw.includes('v'))
        )
      )
    ) {
      context.report(node, `RegExp "v" flag is not supported in ${badBrowser}`)
    }
  }
})
