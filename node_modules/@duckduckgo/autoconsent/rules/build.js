#!/usr/bin/env node
const fs = require("fs");
const path = require("path");
const https = require("https");

const rules = {
  autoconsent: [],
  consentomatic: {}
};

async function readFileJSON(filePath) {
  const data = await fs.promises.readFile(filePath, "utf-8")
  return JSON.parse(data);
}

// merge rules from ./autoconsent into rules.autoconsent array
const autoconsentDir = path.join(__dirname, "autoconsent");
const files = fs.readdirSync(autoconsentDir);
const buildAutoconsent = Promise.all(
  files.map(file => readFileJSON(path.join(autoconsentDir, file)))
).then(r => (rules.autoconsent = r));

// fetch ConsentOMatic rule set and merge with our custom rules
const consentOMaticCommit = "7d7fd2bd6bf2b662350b0eaeca74db6eba155efe";
const consentOMaticUrl = `https://raw.githubusercontent.com/cavi-au/Consent-O-Matic/${consentOMaticCommit}/Rules.json`;
const consentOMaticDir = path.join(__dirname, "consentomatic");
const consentOMaticInclude = [
  'didomi.io', 'oil', 'optanon', 'quantcast2', 'springer', 'wordpressgdpr'
]
const buildConsentOMatic = (async () => {
  const comRules = {};
  const allComRules = await new Promise(resolve => {
    https.get(consentOMaticUrl, res => {
      res.setEncoding("utf-8");
      let content = "";
      res.on("data", data => (content += data));
      res.on("end", () => resolve(JSON.parse(content)));
    });
  });
  consentOMaticInclude.forEach((name) => {
    comRules[name] = allComRules[name];
  })
  try {
    const extraRules = fs.readdirSync(consentOMaticDir);
    await Promise.all(
      extraRules.map(async file => {
        const rule = await readFileJSON(path.join(consentOMaticDir, file));
        // rule name is file name with JSON extension removed
        comRules[file.substring(0, file.length - 5)] = rule;
      })
    );
  } catch(e) {
  }
  rules.consentomatic = comRules;
})();

Promise.all([buildAutoconsent, buildConsentOMatic]).then(() => {
  fs.writeFile(
    path.join(__dirname, "rules.json"),
    JSON.stringify(rules, undefined, "  "),
    () => console.log("Written rules.json")
  );
});
