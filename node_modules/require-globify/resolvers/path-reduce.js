var path = require('path');

module.exports = function(base, files, config) {
  var key, keys, common, file;

  if (Object.keys(files).length === 1) {
    file = Object.keys(files)[0];
    files[file] = path.basename(file);
    return files;
  }

  keys = [];
  for (var file in files) {
    keys.push(files[file].split('/'));
  }

  common = 0;
  while(keys.every(function(key) {
    return key[common] === keys[0][common];
  })) {
    common++;
  }
  common = keys[0].slice(0, common).join('/') + '/';

  for (var file in files) {
    files[file] = files[file].substring(common.length);
  }
  return files;
}
