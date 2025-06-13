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
      description: 'enforce marking high frequency event handlers as passive',
      url: require('../url')(module),
    },
    schema: [],
  },

  create(context) {
    return {
      ['CallExpression[callee.property.name="addEventListener"]']: function (node) {
        const [name, listener, options] = node.arguments
        if (!listener) return
        if (name.type !== 'Literal') return
        if (!passiveEventListenerNames.has(name.value)) return
        if (options && options.type === 'ObjectExpression' && options.properties.some(propIsPassiveTrue)) return
        context.report({node, message: `High Frequency Events like "${name.value}" should be \`passive: true\``})
      },
    }
  },
}
