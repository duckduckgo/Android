module.exports = {
  meta: {
    type: 'suggestion',
    docs: {
      description: 'enforce `for..of` loops over `Array.forEach`',
      url: require('../url')(module),
    },
    schema: [],
  },

  create(context) {
    return {
      CallExpression(node) {
        if (node.callee.property && node.callee.property.name === 'forEach') {
          context.report({node, message: 'Prefer for...of instead of Array.forEach'})
        }
      },
    }
  },
}
