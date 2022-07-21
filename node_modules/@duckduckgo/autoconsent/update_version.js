const fs = require('fs');

const manifestFile = './addon/manifest.json';
const day = new Date()
const version = `${day.getFullYear()}.${day.getMonth() + 1}.${day.getDate()}`
const manifest = require(manifestFile);
manifest.version = version;
fs.writeFileSync(manifestFile, JSON.stringify(manifest, null, 2));