const passiveEventListenerNames = new Set([
  'touchstart',
  'touchmove',
  'touchenter',
  'touchend',
  'touchleave',
  'wheel',
  'mousewheel',
])

const propIsPassiveTrue = prop => prop.key && prop.key.name === 'passive' && prop.value && prop.value.value === true

module.exports = {
  meta: {
    type: 'suggestion',
    docs: {
      description: 'disallow marking a event handler as passive when it has no effect',
      url: require('../url')(module),
    },
    fixable: 'code',
    schema: [],
  },

  create(context) {
    return {
      ['CallExpression[callee.property.name="addEventListener"]']: function (node) {
        const [name, listener, options] = node.arguments
        if (name.type !== 'Literal') return
        if (passiveEventListenerNames.has(name.value)) return
        if (options && options.type === 'ObjectExpression') {
          const i = options.properties.findIndex(propIsPassiveTrue)
          if (i === -1) return
          const passiveProp = options.properties[i]
          const l = options.properties.length
          const source = context.getSourceCode()
          context.report({
            node: passiveProp,
            message: `"${name.value}" event listener is not cancellable and so \`passive: true\` does nothing.`,
            fix(fixer) {
              const removals = []
              if (l === 1) {
                removals.push(options)
                removals.push(...source.getTokensBetween(listener, options))
              } else {
                removals.push(passiveProp)
                if (i > 0) {
                  removals.push(...source.getTokensBetween(options.properties[i - 1], passiveProp))
                } else {
                  removals.push(...source.getTokensBetween(passiveProp, options.properties[i + 1]))
                }
              }
              return removals.map(t => fixer.remove(t))
            },
          })
        }
      },
    }
  },
}
