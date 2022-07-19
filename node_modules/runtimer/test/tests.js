var _getTime = Date.prototype.getTime;
setCurrentTime(0);

var runtimer = require('../');
var assert = require('assert');

describe('Time formatter', function() {

	after(function(){
		Date.prototype.getTime = _getTime.bind(new Date());
	});

	var SEC = 1000;
	var MIN = 60 * SEC;
	var HOUR = 60 * MIN;

	it('should parse milliseconds', function(done) {
		assertTime(50, '50ms', done);
	});

	it('should parse seconds', function(done) {
		assertTime(2 * SEC, '02sec 00ms', done);
	});

	it('should parse seconds and milliseconds', function(done) {
		assertTime(5 * SEC + 123, '05sec 123ms', done);
	});

	it('should parse minutes', function(done) {
		assertTime(3 * MIN, '03min 00sec 00ms', done);
	});

	it('should parse minutes and seconds and milliseconds', function(done) {
		assertTime(3 * MIN + 2 * SEC + 123, '03min 02sec 123ms', done);
	});

	it('should parse hours', function(done) {
		assertTime(4 * HOUR, '04h 00min 00sec 00ms', done);
	});

	it('should parse hours minutes and seconds and milliseconds', function(done) {
		assertTime(4 * HOUR + 3 * MIN + 2 * SEC + 123, '04h 03min 02sec 123ms', done);
	});

	it('should not parse days but hours minutes and seconds and milliseconds', function(done) {
		assertTime(30 * HOUR + 3 * MIN + 2 * SEC + 123, '30h 03min 02sec 123ms', done);
	});

});

describe('Runtimer', function() {

	after(function() {
		runtimer.disable();
	});

	it('should be enabled by default', function(done) {
		var temp = console.log;
		console.log = function(message, date) {	
			console.log = temp;
			done();
		}
		process.emit('exit');
	});

	it('should be enabled', function(done) {
		runtimer.disable();
		runtimer.enable();
		var temp = console.log;
		console.log = function(message, date) {	
			console.log = temp;
			done();
		}
		process.emit('exit');
	});

	it('should be disabled', function() {
		runtimer.disable();
		var observed = false;
		var temp = console.log;
		console.log = function(message, date) {	
			console.log = temp;
			observed = true;
		}
		setTimeout(function() {
			if (!observed) {
				done();
			}
		}, 1000);
	});

});

function assertTime(ms, output, done) {
		var temp = console.log;
		console.log = function(message, date) {		
			console.log = temp;
			assert.equal(date, output);
			done();
		}
		setCurrentTime(ms);
		process.emit('exit');
}

function setCurrentTime(currentTime) {
	Date.prototype.getTime = function() {
		return currentTime;
	}
}