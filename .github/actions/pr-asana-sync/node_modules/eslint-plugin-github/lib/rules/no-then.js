module.exports = {
  meta: {
    type: 'suggestion',
    docs: {
      description: 'enforce using `async/await` syntax over Promises',
      url: require('../url')(module),
    },
    schema: [],
  },

  create(context) {
    return {
      MemberExpression(node) {
        if (node.property && node.property.name === 'then') {
          context.report({node: node.property, message: 'Prefer async/await to Promise.then()'})
        } else if (node.property && node.property.name === 'catch') {
          context.report({node: node.property, message: 'Prefer async/await to Promise.catch()'})
        }
      },
    }
  },
}
