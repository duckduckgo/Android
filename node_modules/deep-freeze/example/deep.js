var deepFreeze = require('../');

deepFreeze(Buffer);
Buffer.x = 5;
console.log(Buffer.x === undefined);

Buffer.prototype.z = 3;
console.log(Buffer.prototype.z === undefined);
