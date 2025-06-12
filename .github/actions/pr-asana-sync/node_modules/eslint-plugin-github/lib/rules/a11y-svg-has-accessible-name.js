const {hasProp} = require('jsx-ast-utils')
const {getElementType} = require('../utils/get-element-type')

module.exports = {
  meta: {
    docs: {
      description: 'SVGs must have an accessible name',
      url: require('../url')(module),
    },
    schema: [],
  },

  create(context) {
    return {
      JSXOpeningElement: node => {
        const elementType = getElementType(context, node)
        if (elementType !== 'svg') return

        // Check if there is a nested title element that is the first non-whitespace child of the `<svg>`
        const childrenWithoutWhitespace = node.parent.children?.filter(({type, value}) =>
          type === 'JSXText' ? value.trim() !== '' : type !== 'JSXText',
        )

        const hasNestedTitleAsFirstChild =
          childrenWithoutWhitespace?.[0]?.type === 'JSXElement' &&
          childrenWithoutWhitespace?.[0]?.openingElement?.name?.name === 'title'

        // Check if `aria-label` or `aria-labelledby` is set
        const hasAccessibleName = hasProp(node.attributes, 'aria-label') || hasProp(node.attributes, 'aria-labelledby')

        // Check if SVG is decorative
        const isDecorative =
          hasProp(node.attributes, 'role', 'presentation') || hasProp(node.attributes, 'aria-hidden', 'true')

        if (elementType === 'svg' && !hasAccessibleName && !isDecorative && !hasNestedTitleAsFirstChild) {
          context.report({
            node,
            message:
              '`<svg>` must have an accessible name. Set `aria-label` or `aria-labelledby`, or nest a `<title>` element. However, if the `<svg>` is purely decorative, hide it with `aria-hidden="true"` or `role="presentation"`.',
          })
        }
      },
    }
  },
}
