prefix = require('./reduce-prefix.js');
postfix = require('./reduce-postfix.js');

module.exports = function(base, files, config) {
  return postfix(base, prefix(base, files, config), config);
}
