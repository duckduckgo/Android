import generateCMPTests from "../playwright/runner";

generateCMPTests('notice-cookie', [
    'https://forum.proxmox.com/',
    'https://usethinkscript.com/'
], {
});