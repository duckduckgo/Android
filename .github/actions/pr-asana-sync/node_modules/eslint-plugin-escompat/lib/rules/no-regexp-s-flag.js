'use strict';

module.exports = (context, badBrowser) => ({
  'Literal[regex]'(node) {
    if (node.regex.flags.includes('s')) {
      context.report(node, `RegExp "s" flag is not supported in ${badBrowser}`)
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
          flags.value.includes('s')
        ) ||
        (
          flags.type === 'TemplateLiteral' &&
          flags.quasis.some(({value: {raw}}) => raw.includes('s'))
        )
      )
    ) {
      context.report(node, `RegExp "s" flag is not supported in ${badBrowser}`)
    }
  }
})
