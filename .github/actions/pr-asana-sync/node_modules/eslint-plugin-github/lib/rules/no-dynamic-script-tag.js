module.exports = {
  meta: {
    type: 'suggestion',
    docs: {
      description: 'disallow creating dynamic script tags',
      url: require('../url')(module),
    },
    schema: [],
  },

  create(context) {
    return {
      'CallExpression[callee.property.name="createElement"][arguments.length > 0]': function (node) {
        if (node.arguments[0].value !== 'script') return

        context.report({
          node: node.arguments[0],
          message: "Don't create dynamic script tags, add them in the server template instead.",
        })
      },
      'AssignmentExpression[left.property.name="type"][right.value="text/javascript"]': function (node) {
        context.report({
          node: node.right,
          message: "Don't create dynamic script tags, add them in the server template instead.",
        })
      },
    }
  },
}
