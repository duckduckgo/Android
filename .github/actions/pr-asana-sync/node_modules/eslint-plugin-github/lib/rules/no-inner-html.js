module.exports = {
  meta: {
    type: 'problem',
    docs: {
      description: 'disallow `Element.prototype.innerHTML` in favor of `Element.prototype.textContent`',
      url: require('../url')(module),
    },
    schema: [],
  },

  create(context) {
    return {
      'MemberExpression[property.name=innerHTML]': function (node) {
        context.report({
          node: node.property,
          message: 'Using innerHTML poses a potential security risk and should not be used. Prefer using textContent.',
        })
      },
    }
  },
}
