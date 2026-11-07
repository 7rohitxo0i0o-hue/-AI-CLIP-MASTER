
/* AI CLIP MASTER - Shruti Speech Worker
 * Runs speech recognition locally using WebAssembly.
 * Audio and transcripts are processed on the device.
 */
'use strict';

importScripts('needle/needle.js'); /* global createNeedle */

var CACHE_NAME = 'shruti-example-v1';
var MAGIC = [0x84, 0x2a, 0xe1, 0x05];
var SR = 16000;

var M = null;
var loaded = false;
var encoder = new TextEncoder();

function send(msg) {
  self.postMessage(msg);
}

function isCact(b) {
  if (!b || b.length < 1024) return false;
  for (var i = 0; i < MAGIC.length; i++) {
    if (b[i] !== MAGIC[i]) return false;
  }
  return true;
}

async function sha256Hex(bytes) {
  var d = new Uint8Array(
    await self.crypto.subtle.digest('SHA-256', bytes)
  );
  return Array.from(d, function (x) {
    return x.toString(16).padStart(2, '0');
  }).join('');
}

async function openCache() {
  try {
    return self.caches
      ? await self.caches.open(CACHE_NAME)
      : null;
  } catch (e) {
    return null;
  }
}

async function getModel(url, sha256, expectedBytes) {
  var cache = await openCache();

  if (cache) {
    var hit = await cache.match(url);

    if (hit) {
      var cached = new Uint8Array(await hit.arrayBuffer());

      if (isCact(cached)) {
        return {
          bytes: cached,
          source: 'cache',
          saved: true
        };
      }

      await cache.delete(url);
    }
  }

  var res = await fetch(url, {
    mode: 'cors',
    credentials: 'omit'
  });

  if (!res.ok) {
    throw new Error(
      'Model download failed: HTTP ' + res.status
    );
  }

  var total =
    Number(res.headers.get('content-length')) ||
    expectedBytes ||
    0;

  var parts = [];
  var got = 0;
  var last = 0;
  var reader = res.body.getReader();

  for (;;) {
    var step = await reader.read();
    if (step.done) break;

    parts.push(step.value);
    got += step.value.length;

    if (Date.now() - last > 100) {
      last = Date.now();
      send({
        type: 'progress',
        loaded: got,
        total: Math.max(total, got)
      });
    }
  }

  var bytes = new Uint8Array(got);
  var at = 0;

  for (var i = 0; i < parts.length; i++) {
    bytes.set(parts[i], at);
    at += parts[i].length;
  }

  if (!isCact(bytes)) {
    throw new Error('Downloaded file is not a valid model.');
  }

  if (sha256 && self.crypto && self.crypto.subtle) {
    var hex = await sha256Hex(bytes);

    if (hex !== sha256) {
      throw new Error('Model SHA-256 verification failed.');
    }
  }

  var saved = false;

  if (cache) {
    try {
      await cache.put(
        url,
        new Response(bytes, {
          headers: {
            'content-type': 'application/octet-stream'
          }
        })
      );
      saved = true;
    } catch (e) {
      saved = false;
    }
  }

  return {
    bytes: bytes,
    source: 'network',
    saved: saved
  };
}

async function startEngine() {
  var url = new URL(
    'needle/needle.wasm',
    self.location.href
  ).href;

  var r = await fetch(url);

  if (!r.ok) {
    throw new Error(
      'Could not load speech engine: HTTP ' + r.status
    );
  }

  return createNeedle({
    wasmBinary: await r.arrayBuffer(),
    print: function () {},
    printErr: function () {}
  });
}

function lastError() {
  try {
    return (
      M.UTF8ToString(M._needle_last_error()) ||
      'Speech engine error.'
    );
  } catch (e) {
    return 'Speech engine error.';
  }
}

function alloc(n) {
  var p = M._malloc(n);

  if (!p) {
    throw new Error('Not enough memory.');
  }

  return p;
}

function copyIn(bytes) {
  var p = alloc(Math.max(4, bytes.length));
  M.HEAPU8.set(bytes, p);
  return p;
}

async function init(msg) {
  try {
    if (!loaded) {
      var both = await Promise.all([
        getModel(msg.url, msg.sha256, msg.bytes),
        startEngine()
      ]);

      var model = both[0];
      M = both[1];

      var ptr = copyIn(model.bytes);

      var rc = M._needle_load(
        ptr,
        BigInt(model.bytes.length)
      );

      M._free(ptr);

      if (rc < 0) {
        throw new Error(lastError());
      }

      if (!(M._needle_models() & 2)) {
        throw new Error('Speech model was not found.');
      }

      loaded = true;

      send({
        type: 'ready',
        source: model.source,
        saved: model.saved
      });
    } else {
      send({
        type: 'ready',
        source: 'memory',
        saved: true
      });
    }
  } catch (e) {
    send({
      type: 'error',
      stage: 'init',
      message: String(e.message || e)
    });
  }
}

function transcribe(msg) {
  var pcm = msg.pcm;

  if (!(pcm instanceof Float32Array)) {
    pcm = new Float32Array(pcm);
  }

  var seconds = pcm.length / SR;

  var p = copyIn(
    new Uint8Array(
      pcm.buffer,
      pcm.byteOffset,
      pcm.length * 4
    )
  );

  var lp = msg.language
    ? copyIn(encoder.encode(msg.language + '\0'))
    : 0;

  var cap = Math.max(
    1 << 16,
    Math.ceil(seconds) * 4096 + (1 << 14)
  );

  var out = alloc(cap);

  try {
    var t0 = performance.now();

    // word_timestamps = 1 enables word-level timing.
    var n = M._needle_transcribe(
      p,
      pcm.length,
      lp,
      0,
      1,
      out,
      cap
    );

    var ms = performance.now() - t0;

    if (n < 0) {
      throw new Error(lastError());
    }

    var r = JSON.parse(M.UTF8ToString(out));

    send({
      type: 'result',
      id: msg.id,
      text: r.text || '',
      words: r.words || [],
      language: r.language || null,
      seconds: seconds,
      ms: ms
    });
  } finally {
    M._free(out);
    M._free(p);

    if (lp) {
      M._free(lp);
    }
  }
}

self.onmessage = function (e) {
  var msg = e.data || {};

  if (msg.type === 'init') {
    return init(msg);
  }

  try {
    if (!loaded) {
      throw new Error('Speech engine is not ready.');
    }

    if (msg.type === 'transcribe') {
      transcribe(msg);
    }
  } catch (err) {
    send({
      type: 'error',
      stage: msg.type,
      id: msg.id,
      message: String(err.message || err)
    });
  }
};
