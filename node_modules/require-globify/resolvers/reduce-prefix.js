module.exports = function(base, files, config) {
  var key, keys, common;

  keys = Object.keys(files);
  if (keys.length === 1) {
    var path = require('path');
    files[keys[0]] = path.basename(keys[0], path.extname(keys[0]));
    return files;
  }

  common = keys[0].substr(0, 1);
  while(keys.every(function(key) {
    return key.indexOf(common) === 0;
  })) {
    common += keys[0].substr(common.length, 1);
  }
  common = common.substr(0, common.length-1);

  for (var i=0, l=keys.length, key=keys[i]; i<l; key=keys[++i]) {
    files[key] = files[key].substr(common.length);
  }
  return files;
}
