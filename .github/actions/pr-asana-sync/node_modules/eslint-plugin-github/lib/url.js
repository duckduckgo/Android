const {homepage, version} = require('../package.json')
const path = require('path')

module.exports = ({id}) => {
  const url = new URL(homepage)
  const rule = path.basename(id, '.js')
  url.hash = ''
  url.pathname += `/blob/v${version}/docs/rules/${rule}.md`
  return url.toString()
}
