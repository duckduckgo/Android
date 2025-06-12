const observerMap = {
  scroll: 'IntersectionObserver',
  resize: 'ResizeObserver',
}
module.exports = {
  meta: {
    type: 'suggestion',
    docs: {
      description: 'disallow poorly performing event listeners',
      url: require('../url')(module),
    },
    schema: [],
  },

  create(context) {
    return {
      ['CallExpression[callee.property.name="addEventListener"]']: function (node) {
        const [name] = node.arguments
        if (name.type !== 'Literal') return
        if (!(name.value in observerMap)) return
        context.report({
          node,
          message: `Avoid using "${name.value}" event listener. Consider using ${observerMap[name.value]} instead`,
        })
      },
    }
  },
}
