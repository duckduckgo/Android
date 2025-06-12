module.exports = {
  meta: {
    type: 'problem',
    docs: {
      description: 'disallow usage of `Element.prototype.blur()`',
      url: require('../url')(module),
    },
    schema: [],
  },
  create(context) {
    return {
      CallExpression(node) {
        if (node.callee.property && node.callee.property.name === 'blur') {
          context.report({node, message: 'Do not use element.blur(), instead restore the focus of a previous element.'})
        }
      },
    }
  },
}
