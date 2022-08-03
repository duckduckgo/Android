const fs = require('fs');

function updateVersion(manifestFile) {
  const day = new Date()
  const version = `${day.getFullYear()}.${day.getMonth() + 1}.${day.getDate()}`
  const manifest = require(manifestFile);
  manifest.version = version;
  fs.writeFileSync(manifestFile, JSON.stringify(manifest, null, 2));
}

updateVersion('./addon/manifest.mv3.json');
updateVersion('./addon/manifest.firefox.json');
