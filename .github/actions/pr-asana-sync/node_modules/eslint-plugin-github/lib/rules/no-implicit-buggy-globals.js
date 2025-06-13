module.exports = {
  meta: {
    type: 'problem',
    docs: {
      description: 'disallow implicit global variables',
      url: require('../url')(module),
    },
    schema: [],
  },

  create(context) {
    return {
      Program() {
        const scope = context.getScope()

        for (const variable of scope.variables) {
          if (variable.writeable) {
            return
          }

          for (const def of variable.defs) {
            if (
              def.type === 'FunctionName' ||
              def.type === 'ClassName' ||
              (def.type === 'Variable' && def.parent.kind === 'const') ||
              (def.type === 'Variable' && def.parent.kind === 'let')
            ) {
              context.report({node: def.node, message: 'Implicit global variable, assign as global property instead.'})
            }
          }
        }
      },
    }
  },
}
