module.exports = {
  meta: {
    type: 'problem',
    docs: {
      description: 'disallow `event.currentTarget` calls inside of async functions',
      url: require('../url')(module),
    },
    schema: [],
  },

  create(context) {
    const scopeDidWait = new WeakSet()

    return {
      AwaitExpression() {
        scopeDidWait.add(context.getScope(), true)
      },
      MemberExpression(node) {
        if (node.property && node.property.name === 'currentTarget') {
          const scope = context.getScope()
          if (scope.block.async && scopeDidWait.has(scope)) {
            context.report({node, message: 'event.currentTarget inside an async function is error prone'})
          }
        }
      },
    }
  },
}
