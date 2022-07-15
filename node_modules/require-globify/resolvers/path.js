var path = require('path');

module.exports = function(base, files, config) {
  var keys, key, relative;

  base = path.dirname(base);

  keys = Object.keys(files);
  for (var i=0, l=keys.length, key=keys[i]; i<l; key=keys[++i]) {
    relative = path.relative(base, path.resolve(base, key)).replace(/\\/g, '/');
    if (relative.indexOf('../') !== 0) {
      relative = './' + relative;
    }
    files[key] = relative;
  }
  return files;
}
