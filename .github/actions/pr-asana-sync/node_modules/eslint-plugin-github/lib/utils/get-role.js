const {getProp, getLiteralPropValue} = require('jsx-ast-utils')
const {elementRoles} = require('aria-query')
const {getElementType} = require('./get-element-type')
const ObjectMap = require('./object-map')

const elementRolesMap = cleanElementRolesMap()

/*
  Returns an element roles map which uses `aria-query`'s elementRoles as the foundation.
  We additionally clean the data so we're able to fetch a role using a key we construct based on the node we're looking at.
  In a few scenarios, we stray from the roles returned by `aria-query` and hard code the mapping.
*/
function cleanElementRolesMap() {
  const rolesMap = new ObjectMap()

  for (const [key, value] of elementRoles.entries()) {
    // - Remove empty `attributes` key
    if (!key.attributes || key.attributes?.length === 0) {
      delete key.attributes
    }
    rolesMap.set(key, value)
  }
  // Remove insufficiently-disambiguated `menuitem` entry
  rolesMap.delete({name: 'menuitem'})
  // Disambiguate `menuitem` and `menu` roles by `type`
  rolesMap.set({name: 'menuitem', attributes: [{name: 'type', value: 'command'}]}, ['menuitem'])
  rolesMap.set({name: 'menuitem', attributes: [{name: 'type', value: 'radio'}]}, ['menuitemradio'])
  rolesMap.set({name: 'menuitem', attributes: [{name: 'type', value: 'toolbar'}]}, ['toolbar'])
  rolesMap.set({name: 'menu', attributes: [{name: 'type', value: 'toolbar'}]}, ['toolbar'])

  /* These have constraints defined in aria-query's `elementRoles` which depend on knowledge of ancestor roles which we cant accurately determine in a linter context.
     However, we benefit more from assuming the role, than assuming it's generic or undefined so we opt to hard code the mapping */
  rolesMap.set({name: 'aside'}, ['complementary']) // `aside` still maps to `complementary` in https://www.w3.org/TR/html-aria/#docconformance.
  rolesMap.set({name: 'li'}, ['listitem']) // `li` can be generic if it's not within a list but we would never want to render `li` outside of a list.

  return rolesMap
}

/*
  Determine role of an element, based on its name and attributes.
  We construct a key and look up the element's role in `elementRolesMap`.
  If there is no match, we return undefined.
*/
function getRole(context, node) {
  // Early return if role is explicitly set
  const explicitRole = getLiteralPropValue(getProp(node.attributes, 'role'))
  if (explicitRole) {
    return explicitRole
  } else if (getProp(node.attributes, 'role')) {
    // If role is set to anything other than a literal prop
    return undefined
  }

  // Assemble a key for looking-up the element’s role in the `elementRolesMap`
  // - Get the element’s name
  const key = {name: getElementType(context, node)}

  for (const prop of [
    'aria-label',
    'aria-labelledby',
    'alt',
    'type',
    'size',
    'role',
    'href',
    'multiple',
    'scope',
    'name',
  ]) {
    if ((prop === 'aria-labelledby' || prop === 'aria-label') && !['section', 'form'].includes(key.name)) continue
    if (prop === 'name' && key.name !== 'form') continue
    if (prop === 'href' && key.name !== 'a' && key.name !== 'area') continue
    if (prop === 'alt' && key.name !== 'img') continue

    const propOnNode = getProp(node.attributes, prop)

    if (!('attributes' in key)) {
      key.attributes = []
    }
    // Disambiguate "undefined" props
    if (propOnNode === undefined && prop === 'alt' && key.name === 'img') {
      key.attributes.push({name: prop, constraints: ['undefined']})
      continue
    }

    const value = getLiteralPropValue(propOnNode)
    if (propOnNode) {
      if (
        prop === 'href' ||
        prop === 'aria-labelledby' ||
        prop === 'aria-label' ||
        prop === 'name' ||
        (prop === 'alt' && value !== '')
      ) {
        key.attributes.push({name: prop, constraints: ['set']})
      } else if (value || (value === '' && prop === 'alt')) {
        key.attributes.push({name: prop, value})
      }
    }
  }

  // - Remove empty `attributes` key
  if (!key.attributes || key.attributes?.length === 0) {
    delete key.attributes
  }

  // Get the element’s implicit role
  return elementRolesMap.get(key)?.[0]
}

module.exports = {getRole}
