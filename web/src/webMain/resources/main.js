/*
 * The page: read the form, ask the Kotlin bundle for the code, draw it, and let it be
 * downloaded.
 *
 * The bundle hands over `encode(text, errorCorrectionLevel, version)`, which returns the
 * modules as lines of '1' for dark and '0' for light, or an empty string when the text does
 * not fit any version. Everything else - the form, the canvas, the SVG, the clipboard, the
 * downloads - is here, so the Kotlin side stays a demonstration of the library.
 */
(function () {
	'use strict';

	/* Extra modules of blank margin around the code, at least four as the specification
	   asks, so a scanner can find the symbol. */
	var QUIET_ZONE = 4;

	var STATUS = {
		empty: '…',
		tooLong: 'That text is too long for any version. Try a higher error correction level or less text.',
		ready: function (size, version) {
			return version === 0
				? size + ' × ' + size + ' modules'
				: 'Version ' + version + ' — ' + size + ' × ' + size + ' modules';
		},
	};

	function element(id) {
		var found = document.getElementById(id);
		if (!found) throw new Error('index.html has no #' + id);
		return found;
	}

	/* The modules, as rows of booleans, plus the size. */
	function parseMatrix(rows) {
		if (!rows) return null;
		var lines = rows.split('\n').filter(function (line) {
			return line.length > 0;
		});
		if (lines.length === 0) return null;
		return {
			size: lines[0].length,
			isDark: function (x, y) {
				return lines[y].charAt(x) === '1';
			},
		};
	}

	/* The version the encoder settled on, read back from the size: 17 + 4 per version. */
	function versionOf(size) {
		return (size - 17) / 4;
	}

	function svgPath(matrix, scale) {
		var side = (matrix.size + QUIET_ZONE * 2) * scale;
		var path = '';
		for (var y = 0; y < matrix.size; y++) {
			for (var x = 0; x < matrix.size; x++) {
				if (!matrix.isDark(x, y)) continue;
				/* One run at a time, so the path stays short on a dense code. */
				var run = 1;
				while (x + run < matrix.size && matrix.isDark(x + run, y)) run++;
				path += 'M' + ((x + QUIET_ZONE) * scale) + ' ' + ((y + QUIET_ZONE) * scale) +
					'h' + (run * scale) + 'v' + scale + 'h' + (-run * scale) + 'z';
				x += run - 1;
			}
		}
		return { side: side, path: path };
	}

	function download(name, type, contents) {
		var blob = new Blob([contents], { type: type });
		var url = URL.createObjectURL(blob);
		var link = document.createElement('a');
		link.href = url;
		link.download = name;
		document.body.appendChild(link);
		link.click();
		document.body.removeChild(link);
		URL.revokeObjectURL(url);
	}

	function start(encode) {
		var textInput = element('text');
		var levelSelect = element('level');
		var versionSelect = element('version');
		var scaleSelect = element('scale');
		var canvas = element('code');
		var status = element('status');
		var buttons = ['download-png', 'download-svg', 'copy'].map(element);

		var context = canvas.getContext('2d');
		var current = null;

		/* Fill the version list once; 0 means "smallest version the text fits in". */
		for (var version = 1; version <= 40; version++) {
			var option = document.createElement('option');
			option.value = String(version);
			option.textContent = String(version);
			versionSelect.appendChild(option);
		}

		function draw(scale) {
			if (!current) {
				canvas.removeAttribute('width');
				canvas.removeAttribute('height');
				return;
			}
			var side = (current.size + QUIET_ZONE * 2) * scale;
			canvas.width = side;
			canvas.height = side;

			context.fillStyle = '#ffffff';
			context.fillRect(0, 0, side, side);
			context.fillStyle = '#000000';
			for (var y = 0; y < current.size; y++) {
				for (var x = 0; x < current.size; x++) {
					if (current.isDark(x, y)) {
						context.fillRect((x + QUIET_ZONE) * scale, (y + QUIET_ZONE) * scale, scale, scale);
					}
				}
			}
		}

		function update() {
			var text = textInput.value;
			var version = parseInt(versionSelect.value, 10);
			var matrix = parseMatrix(encode(text, levelSelect.value, version));
			current = matrix;

			buttons.forEach(function (button) {
				button.disabled = !matrix;
			});
			draw(parseInt(scaleSelect.value, 10));

			if (text.length === 0) {
				status.className = 'status';
				status.textContent = STATUS.empty;
			} else if (!matrix) {
				status.className = 'status error';
				status.textContent = STATUS.tooLong;
			} else {
				var settled = version === 0 ? versionOf(matrix.size) : version;
				status.className = 'status';
				status.textContent = STATUS.ready(matrix.size, version === 0 ? 0 : settled);
			}
		}

		var pending = null;
		/* Redrawing a large code on every keystroke is wasted work, so wait for a pause. */
		function scheduleUpdate() {
			if (pending) clearTimeout(pending);
			pending = setTimeout(update, 120);
		}

		textInput.addEventListener('input', scheduleUpdate);
		levelSelect.addEventListener('change', update);
		versionSelect.addEventListener('change', update);
		scaleSelect.addEventListener('change', update);

		element('download-png').addEventListener('click', function () {
			if (!current) return;
			canvas.toBlob(function (blob) {
				var url = URL.createObjectURL(blob);
				var link = document.createElement('a');
				link.href = url;
				link.download = 'qr-code.png';
				document.body.appendChild(link);
				link.click();
				document.body.removeChild(link);
				URL.revokeObjectURL(url);
			}, 'image/png');
		});

		element('download-svg').addEventListener('click', function () {
			if (!current) return;
			var scale = parseInt(scaleSelect.value, 10);
			var drawn = svgPath(current, scale);
			download(
				'qr-code.svg',
				'image/svg+xml',
				'<svg xmlns="http://www.w3.org/2000/svg" width="' + drawn.side + '" height="' +
					drawn.side + '" viewBox="0 0 ' + drawn.side + ' ' + drawn.side + '" shape-rendering="crispEdges">' +
					'<rect width="' + drawn.side + '" height="' + drawn.side + '" fill="#fff"/>' +
					'<path d="' + drawn.path + '" fill="#000"/>' +
					'</svg>\n',
			);
		});

		element('copy').addEventListener('click', function () {
			if (!current) return;
			var rows = [];
			for (var y = 0; y < current.size; y++) {
				var row = '';
				for (var x = 0; x < current.size; x++) {
					row += current.isDark(x, y) ? '##' : '  ';
				}
				rows.push(row);
			}
			var button = element('copy');
			navigator.clipboard.writeText(rows.join('\n')).then(function () {
				button.textContent = 'Copied';
				setTimeout(function () {
					button.textContent = 'Copy as text';
				}, 900);
			}, function (error) {
				console.error('Failed to copy the code: ', error);
			});
		});

		update();
	}

	/*
	 * Called by the Kotlin bundle as soon as it runs, with the encoder. This file is loaded
	 * before the bundle, so the hook is in place by the time it is called - and it has to
	 * be that way round, because the Wasm build starts up after the page has loaded.
	 */
	window.qrReady = start;
})();
