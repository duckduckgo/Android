module.exports = {
  meta: {
    type: 'problem',
    docs: {
      description: 'enforce usage of `Element.prototype.getAttribute` instead of `Element.prototype.datalist`',
      url: require('../url')(module),
    },
    schema: [],
  },

  create(context) {
    return {
      MemberExpression(node) {
        if (node.property && node.property.name === 'dataset') {
          context.report({node, message: "Use getAttribute('data-your-attribute') instead of dataset."})
        }
      },
    }
  },
}
