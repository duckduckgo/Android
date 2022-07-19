
var startTime = now();
var enabled = true;

process.on('exit', function() {
    var diffTime = now() - startTime;
    if (enabled) {
    	console.log('Script execution time: %s', formatTime(diffTime));
	}
});

function now() {
	return new Date().getTime();
}

function formatTime(time) {
	var str = '';

	var hours = Math.floor(time / 1000 / 60 / 60);
	var minutes = Math.floor(time / 1000 / 60) % 60;
	var seconds = Math.floor(time / 1000) % 60;
	var milliseconds = time % 1000;

	if (hours > 0) {
		str += addZero(hours) + 'h ';
	}
	if (minutes > 0 || hours > 0) {
		str += addZero(minutes) + 'min ';
	}
	if (seconds > 0 || minutes > 0 || hours > 0) {
		str += addZero(seconds) + 'sec ';
	}
	str += addZero(milliseconds)+ 'ms ';
	
	//trim right
	while (str.charAt(str.length - 1) == ' ') {
		str = str.substring(0, str.length - 1);
	}
	return str;
}

function addZero(value) {
	return value < 10 ? "0" + value : value;
}

module.exports.disable = function() {
	enabled = false;
}
module.exports.enable = function() {
	enabled = true;
}