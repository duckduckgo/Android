'use strict';

module.exports = {
  meta: {
    fixable: 'code'
  },
  create: (context, badBrowser) => ({
    'Literal[raw=/_/][value>=0], Literal[raw=/_/][value<=0]'(node) {
      context.report({
        node,
        message: `Numeric Separators are not supported in ${badBrowser}`,
        fix: fixer => fixer.replaceText(node, String(node.value))
      })
    }
  })
}
