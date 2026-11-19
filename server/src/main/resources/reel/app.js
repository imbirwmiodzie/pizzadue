'use strict';
/*
 * The pizza reel in the browser. The page films with the phone's own camera at 1080p / 30 fps
 * (never HDR) and edits while it films: a canvas gets every camera frame with the reel's words
 * drawn on, and the recorder only runs during the seconds that go into the reel. The guide asks
 * for the shots in the reel's order, starting with the crunch (the hook), so the reel is ready
 * the moment the last shot ends. Nothing leaves the phone.
 *
 * The drawing follows the Android app (ReelOverlays, CardRenderer.qualitySeal, Pizzaiolo, Soundtrack).
 * Link parameters fill in the pizzaiolo's numbers (all optional, remembered by the browser):
 *   n=<pizza number> bake=<seconds in the oven> hyd=<hydration %> ferm=<fermentation hours>
 *   temp=<oven °C> goal=<series length> by=<@handle> seal=qualita|casa|amore|none
 *   style=stats|asmr|funny|clean tag=0 (no "made with VibePizzaiolo")
 */
(() => {
  const W = 1080, H = 1920, FPS = 30;
  const COUNTDOWN = 3;
  const STAMP_HIT = 0.55, VOICE_AT = 0.4, LOOP_FADE = 0.3;
  const LABEL_Y = 1300, SAFE_RIGHT = 920;

  const C = {
    flame: '#FFB020', ink: '#1A1E23', fg: '#F3EFE6', muted: '#C9CED4',
    gold: '#E9A825', goldDark: '#8A5A10', sealRed: '#B71C1C',
    itGreen: '#009246', itWhite: '#F8F5EE', itRed: '#CE2B37',
  };
  const SANS = 'system-ui, -apple-system, "Segoe UI", Roboto, sans-serif';
  const MONO = 'ui-monospace, "SF Mono", Menlo, "Roboto Mono", monospace';

  // ---------------- the pizzaiolo's choices: link, then what this browser remembers ----------------

  const STYLES = [
    { id: 'stats', label: 'Stats', hint: 'your numbers on the shots' },
    { id: 'asmr', label: 'ASMR', hint: 'the sound, almost no text' },
    { id: 'funny', label: 'Funny', hint: 'the cartoon pizzaiolo talks' },
    { id: 'clean', label: 'No text', hint: 'write your own in Instagram or TikTok' },
  ];
  const SEALS = {
    qualita: { big: 'QUALITÀ', small: 'SUPERIORE', label: 'Qualità superiore' },
    casa: { big: 'FATTA', small: 'IN CASA', label: 'Fatta in casa' },
    amore: { big: 'CON', small: 'AMORE', label: 'Con amore' },
    none: null,
  };
  const NUMBERS = {
    n: { label: 'Pizza nº', max: 99999 },
    bake: { label: 'Seconds in the oven', max: 3600 },
    hyd: { label: 'Hydration %', max: 100 },
    ferm: { label: 'Fermentation hours', max: 240 },
    temp: { label: 'Oven °C', max: 600 },
    goal: { label: 'Series of… (100)', max: 1000 },
  };
  const STORE = 'vibepizzaiolo.reel';

  const opt = (() => {
    let saved = {};
    try { saved = JSON.parse(localStorage.getItem(STORE) || '{}') || {}; } catch (_) { saved = {}; }
    const q = new URLSearchParams(location.search);
    const o = { style: 'stats', by: '', seal: 'qualita', tag: true };
    for (const k of Object.keys(NUMBERS)) o[k] = null;
    const take = (src) => {
      if (STYLES.some((s) => s.id === src.style)) o.style = src.style;
      if (typeof src.by === 'string') o.by = src.by.trim().slice(0, 30);
      if (src.seal in SEALS) o.seal = src.seal;
      if (src.tag === false || src.tag === '0') o.tag = false;
      if (src.tag === true || src.tag === '1') o.tag = true;
      for (const [k, spec] of Object.entries(NUMBERS)) {
        const v = src[k];
        if (v === null || v === '') o[k] = null;
        else if (v !== undefined && /^\d{1,5}$/.test(String(v)) && Number(v) <= spec.max) o[k] = Number(v);
      }
    };
    take(saved);
    take(Object.fromEntries(q.entries()));
    return o;
  })();
  function remember() { try { localStorage.setItem(STORE, JSON.stringify(opt)); } catch (_) { /* private mode */ } }

  const clock = (s) => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
  const talk = () => opt.style === 'stats' || opt.style === 'funny';
  const sealWords = () => (talk() ? SEALS[opt.seal] : null);
  const handleText = () => {
    const h = opt.by.trim();
    if (!h) return '';
    return h.startsWith('@') || h.includes(' ') ? h : `@${h}`;
  };
  const pizzaTitle = () => {
    if (!opt.n) return 'Pizza fatta in casa';
    return opt.goal && opt.n <= opt.goal ? `Pizza nº ${opt.n} / ${opt.goal}` : `Pizza nº ${opt.n}`;
  };
  function stats() {
    const out = [];
    if (opt.hyd) out.push(`${opt.hyd}% idratazione`);
    if (opt.ferm) out.push(`${opt.ferm} h lievitazione`);
    if (opt.temp) out.push(`${opt.temp} °C`);
    if (opt.bake) out.push(`${clock(opt.bake)} in forno`);
    return out;
  }

  /** The post's text: a hook line, the numbers, the hashtags people search, the app's tag last. */
  function caption() {
    const nums = [
      opt.bake ? `${clock(opt.bake)} in the oven${opt.temp ? ` at ${opt.temp} °C` : ''}` : (opt.temp ? `${opt.temp} °C oven` : null),
      opt.hyd ? `${opt.hyd}% hydration` : null,
      opt.ferm ? `${opt.ferm} h ferment` : null,
    ].filter(Boolean);
    const series = opt.n && opt.goal && opt.n <= opt.goal ? ` of ${opt.goal}` : '';
    const head = opt.n ? `Pizza nº ${opt.n}${series} 🍕` : 'Homemade pizza 🍕';
    const tags = ['#pizzanapoletana', '#neapolitanpizza', '#homemadepizza', '#cornicione', '#pizzaoven', '#pizzalover'];
    if (opt.goal) tags.push(`#${opt.goal}pizzas`);
    tags.push('#VibePizzaiolo');
    return [
      nums.length ? `${head} ${nums.join(' · ')}` : head,
      'Sound on for the crunch 🔊',
      `Film yours: ${location.origin}/reel`,
      '',
      tags.join(' '),
    ].join('\n');
  }

  // ---------------- the shots: filmed in the reel's order ----------------

  /**
   * What to film, phase by phase, and which seconds of each phase go into the reel. A kept piece
   * is [from, to, kind, index, endingFrom]: endingFrom is where, inside it, the ending begins.
   */
  function phases() {
    const list = [
      { title: 'IL CRUNCH', say: 'Close to the rim: press it with a finger, 2–3 times. Let us hear the crunch!', len: 3, keep: [[0.6, 1.8, 'HOOK']] },
      { title: "DALL'ALTO", say: 'Hold the phone flat above the whole pizza.', len: 3, keep: [[0.5, 2.5, 'TOP']] },
      { title: 'TUTTO INTORNO', say: 'Go slowly once around it, low, at the height of the rim.', len: 8, keep: [[1.0, 2.4, 'AROUND', 0], [5.2, 6.6, 'AROUND', 1]] },
      { title: 'IL SOTTO', say: 'Lift the edge with the peel or a fork: show the spots underneath.', len: 3, keep: [[0.8, 2.3, 'UNDER']] },
    ];
    if (sealWords()) list.push({ title: 'IL SIGILLO', say: 'Back above the pizza and hold still: the seal lands on it.', len: 3, keep: [[0.4, 2.6, 'STAMP']] });
    list.push({ title: 'LA FETTA', say: 'Pull out a slice slowly, cheese stretching, then hold it up.', len: 5, keep: [[0.8, 5.0, 'SLICE', 0, 2.1]] });
    return list;
  }

  /** The kept pieces in seconds of filming. */
  function shots(list) {
    const out = [];
    let at = 0;
    for (const p of list) {
      for (const [from, to, kind, index = 0, endingFrom = null] of p.keep) {
        out.push({ from: at + from, to: at + to, kind, index, endingFrom });
      }
      at += p.len;
    }
    out[out.length - 1].last = true;
    return out;
  }

  const app = document.getElementById('app');
  const el = (tag, attrs = {}, ...kids) => {
    const e = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs)) {
      if (k === 'class') e.className = v;
      else if (k.startsWith('on')) e.addEventListener(k.slice(2), v);
      else if (v !== false && v != null) e.setAttribute(k, v === true ? '' : v);
    }
    for (const k of kids) if (k != null) e.append(k);
    return e;
  };
  const show = (...kids) => { app.replaceChildren(...kids); };

  // ---------------- what the browser can do ----------------

  const MIME = [
    'video/mp4;codecs=avc1.640028,mp4a.40.2',
    'video/mp4;codecs=avc1.42E01F,mp4a.40.2',
    'video/mp4;codecs=avc1,mp4a.40.2',
    'video/mp4',
    'video/webm;codecs=vp9,opus',
    'video/webm;codecs=vp8,opus',
    'video/webm',
  ];
  const pickMime = () => (window.MediaRecorder && MIME.find((m) => MediaRecorder.isTypeSupported(m))) || null;

  function problem() {
    if (!window.isSecureContext) return 'This page needs a secure (https://) address to use the camera.';
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) return 'This browser cannot use the camera. Open the link in Chrome or Safari.';
    if (!window.MediaRecorder || !HTMLCanvasElement.prototype.captureStream) return 'This browser cannot record video. Open the link in Chrome or Safari.';
    if (!pickMime()) return 'This browser cannot save video. Open the link in Chrome or Safari.';
    return null;
  }

  // ---------------- screens ----------------

  function start() {
    const p = problem();
    const list = phases();
    const film = list.reduce((s, ph) => s + ph.len, 0);
    const steps = el('ol', { class: 'steps' }, ...list.map((ph, i) => el('li', {},
      el('span', { class: 'n' }, String(i + 1)),
      el('div', {}, el('div', { class: 'name' }, ph.title), el('div', { class: 'sub' }, ph.say)),
      el('span', { class: 'secs' }, `${ph.len} s`))));

    const styleHint = el('p', { class: 'fine' }, STYLES.find((s) => s.id === opt.style).hint);
    const styles = el('div', { class: 'chips', role: 'radiogroup', 'aria-label': 'Style' }, ...STYLES.map((s) => el('button', {
      class: 'chip', role: 'radio', 'aria-checked': String(s.id === opt.style),
      onclick: () => { opt.style = s.id; remember(); start(); },
    }, s.label)));

    const field = (k, spec) => el('label', { class: 'field' }, el('span', {}, spec.label),
      el('input', {
        type: 'number', inputmode: 'numeric', min: '0', max: String(spec.max), value: opt[k] == null ? '' : String(opt[k]),
        onchange: (e) => {
          const v = e.target.value.trim();
          opt[k] = /^\d{1,5}$/.test(v) && Number(v) > 0 && Number(v) <= spec.max ? Number(v) : null;
          e.target.value = opt[k] == null ? '' : String(opt[k]);
          remember();
        },
      }));
    const handle = el('label', { class: 'field wide' }, el('span', {}, 'Your @handle or name, big at the end'),
      el('input', { type: 'text', maxlength: '30', autocomplete: 'off', placeholder: '@pizza_di_marco', value: opt.by, onchange: (e) => { opt.by = e.target.value.trim().slice(0, 30); remember(); } }));
    const seal = el('label', { class: 'field wide' }, el('span', {}, 'The seal says'),
      el('select', { onchange: (e) => { opt.seal = e.target.value; remember(); start(); } },
        ...Object.entries(SEALS).map(([id, s]) => el('option', { value: id, selected: id === opt.seal }, s ? s.label : 'No seal'))));
    const tag = el('label', { class: 'check' },
      el('input', { type: 'checkbox', checked: opt.tag, onchange: (e) => { opt.tag = e.target.checked; remember(); } }),
      el('span', {}, 'Small “made with VibePizzaiolo” at the end'));
    const filled = Object.keys(NUMBERS).some((k) => opt[k] != null) || opt.by;
    const yours = el('details', { class: 'yours', open: !filled },
      el('summary', {}, 'You and your numbers'),
      el('div', { class: 'fields' }, handle, ...Object.entries(NUMBERS).map(([k, s]) => field(k, s)), seal, tag),
      el('p', { class: 'fine' }, 'All optional; this browser remembers them.'));

    show(el('div', { class: 'page' },
      el('div', {},
        el('div', { class: 'brand' }, 'VibePizzaiolo'),
        el('h1', {}, 'Pizza reel'),
        el('p', { class: 'lead' }, `Film your pizza for ${film} seconds and get a vertical reel that opens on the crunch, ready for Instagram, TikTok or WhatsApp.`)),
      el('div', {}, styles, styleHint),
      yours,
      steps,
      el('p', { class: 'fine' }, 'The page tells you what to film while it records. The reel is made on your phone; nothing is uploaded.'),
      p ? el('p', { class: 'error' }, p) : null,
      el('div', { class: 'actions' },
        p ? null : el('button', { class: 'btn', onclick: openCamera }, 'Open the camera'))));
  }

  function failed(message) {
    stopAll();
    show(el('div', { class: 'page' },
      el('div', {}, el('div', { class: 'brand' }, 'VibePizzaiolo'), el('h1', {}, 'No reel this time')),
      el('p', { class: 'error' }, message),
      el('div', { class: 'actions' }, el('button', { class: 'btn', onclick: openCamera }, 'Try again'),
        el('button', { class: 'btn ghost', onclick: start }, 'Back'))));
  }

  function done(blob, type, coverBlob) {
    stopAll();
    const ext = type.includes('mp4') ? 'mp4' : 'webm';
    const name = `vibepizzaiolo-reel.${ext}`;
    const url = URL.createObjectURL(blob);
    const coverUrl = coverBlob ? URL.createObjectURL(coverBlob) : null;
    const file = new File([blob], name, { type: type.split(';')[0] });
    const text = caption();
    const canShare = !!(navigator.canShare && navigator.canShare({ files: [file] }));
    const copied = el('span', { class: 'copied' });
    const copy = async () => {
      try { await navigator.clipboard.writeText(text); copied.textContent = 'Copied: paste it in your post'; } catch (_) { copied.textContent = 'Select the text above to copy it'; }
    };
    const share = async () => {
      // Instagram and TikTok drop text that comes with a video: it goes to the clipboard first
      try { await navigator.clipboard.writeText(text); } catch (_) { /* not allowed here */ }
      try { await navigator.share({ files: [file], title: 'La pizza è pronta!', text }); } catch (_) { /* closed */ }
    };
    show(el('div', { class: 'page' },
      el('div', {}, el('div', { class: 'brand' }, 'VibePizzaiolo'), el('h1', {}, 'La pizza è pronta!')),
      el('video', { class: 'result', src: url, controls: true, playsinline: true, loop: true, preload: 'auto' }),
      ext === 'webm' ? el('p', { class: 'fine' }, 'This browser saves the reel as WebM, which Instagram may not take. Chrome on Android and Safari on iPhone save MP4.') : null,
      el('div', { class: 'caption' }, el('pre', {}, text), el('div', { class: 'row' }, el('button', { class: 'btn small ghost', onclick: copy }, 'Copy the caption'), copied)),
      el('p', { class: 'fine' }, 'Add a trending sound in Instagram or TikTok: the crunch stays loud under it. Pick the cover picture as the reel’s cover.'),
      el('div', { class: 'actions' },
        canShare ? el('button', { class: 'btn', onclick: share }, 'Share the reel') : null,
        el('a', { class: canShare ? 'btn ghost' : 'btn', href: url, download: name }, 'Save the video'),
        coverUrl ? el('a', { class: 'btn ghost', href: coverUrl, download: 'vibepizzaiolo-cover.jpg' }, 'Save the cover picture') : null,
        el('button', { class: 'btn ghost', onclick: () => { URL.revokeObjectURL(url); if (coverUrl) URL.revokeObjectURL(coverUrl); start(); } }, 'Film again'))));
  }

  // ---------------- camera ----------------

  let cam = null;      // the camera's MediaStream
  let ac = null;       // AudioContext for the recording's sound, the thud and the voice
  let voice = null;    // decoded "La pizza è pronta!"
  let run = null;      // the recording under way
  let wake = null;

  function stopAll() {
    if (run) {
      run.stopped = true;
      cancelAnimationFrame(run.raf);
      if (run.rec && run.rec.state !== 'inactive') { try { run.rec.stop(); } catch (_) { /* already stopping */ } }
      run = null;
    }
    if (cam) { cam.getTracks().forEach((t) => t.stop()); cam = null; }
    if (wake) { wake.release().catch(() => {}); wake = null; }
  }

  async function getCamera() {
    const video = { facingMode: { ideal: 'environment' }, width: { ideal: 1920 }, height: { ideal: 1080 }, frameRate: { ideal: 30, max: 30 } };
    const audio = { echoCancellation: false, noiseSuppression: false, autoGainControl: false };
    try {
      return await navigator.mediaDevices.getUserMedia({ video, audio });
    } catch (e) {
      if (e && e.name === 'NotAllowedError') {
        // the microphone may be the one refused: film without the crunch rather than not at all
        try { return await navigator.mediaDevices.getUserMedia({ video, audio: false }); } catch (_) { throw e; }
      }
      throw e;
    }
  }

  function cameraError(e) {
    const n = e && e.name;
    if (n === 'NotAllowedError' || n === 'SecurityError') return 'The camera is blocked for this page. Allow the camera in the browser (the icon next to the address), then try again.';
    if (n === 'NotFoundError' || n === 'OverconstrainedError') return 'No camera was found on this device.';
    if (n === 'NotReadableError') return 'Another app is using the camera. Close it and try again.';
    return `The camera did not start (${n || e}).`;
  }

  async function openCamera() {
    stopAll();
    show(el('p', { class: 'loading' }, '🍕'));
    try {
      if (!ac) ac = new (window.AudioContext || window.webkitAudioContext)();
      ac.resume().catch(() => {});
    } catch (_) { ac = null; }
    if (ac && !voice) {
      fetch('/reel/pronta.mp3').then((r) => r.arrayBuffer()).then((b) => ac.decodeAudioData(b)).then((b) => { voice = b; }).catch(() => {});
    }
    try {
      cam = await getCamera();
    } catch (e) {
      failed(cameraError(e));
      return;
    }
    const list = phases();
    const video = el('video', { playsinline: true, muted: true, autoplay: true });
    video.muted = true;
    video.srcObject = cam;
    const canvas = el('canvas', { width: String(W), height: String(H) });
    const guide = guideBox(list);
    guide.set(0, 0, false);
    const count = el('div', { class: 'count' });
    const shutter = el('button', { class: 'shutter', 'aria-label': 'Start filming' });
    const close = el('button', { class: 'close', 'aria-label': 'Close', onclick: () => { stopAll(); start(); } }, '✕');
    const stage = el('div', { class: 'stage' }, canvas, video, guide.box, count, el('div', { class: 'bottom' }, shutter), close);
    // listening from the start: a tap while the camera is still warming up counts too
    shutter.addEventListener('click', () => {
      shutter.remove();
      if (ac) ac.resume().catch(() => {});
      film({ video, canvas, guide, count, list });
    }, { once: true });
    show(el('div', { class: 'stage-wrap' }, stage));
    try { await video.play(); } catch (_) { /* autoplay muted works everywhere it matters */ }
  }

  function guideBox(list) {
    const title = el('span', {});
    const left = el('span', { class: 'left' });
    const rec = el('span', { class: 'rec' });
    const say = el('div', { class: 'say' });
    const bars = list.map((p) => { const b = el('span', {}, el('i')); b.style.flex = String(p.len); return b; });
    const box = el('div', { class: 'guide' }, el('div', { class: 'title' }, rec, title, left), say, el('div', { class: 'bars' }, ...bars));
    return {
      box,
      set(phase, secs, filming) {
        const p = list[phase];
        rec.style.display = filming ? '' : 'none';
        title.textContent = filming ? p.title : `First: ${p.title}`;
        say.textContent = p.say;
        left.textContent = filming ? `${Math.ceil(p.len - secs)}` : '';
        bars.forEach((b, i) => { b.firstChild.style.width = `${i < phase ? 100 : i === phase ? (100 * secs) / p.len : 0}%`; });
      },
    };
  }

  // ---------------- filming and editing ----------------

  async function film({ video, canvas, guide, count, list }) {
    const ctx = canvas.getContext('2d');
    const mime = pickMime();
    const keep = shots(list);
    const filmLen = list.reduce((s, p) => s + p.len, 0);
    const audioOut = ac ? ac.createMediaStreamDestination() : null;
    // the recording's own sound under every shot: evened out, then louder on the crunch
    let mic = null;
    if (ac && cam.getAudioTracks().length) {
      const comp = ac.createDynamicsCompressor();
      comp.threshold.value = -30; comp.knee.value = 10; comp.ratio.value = 6; comp.attack.value = 0.003; comp.release.value = 0.15;
      mic = ac.createGain();
      mic.gain.value = 0;
      ac.createMediaStreamSource(cam).connect(comp).connect(mic).connect(audioOut);
    }
    const level = (k) => (!k ? 0 : k.kind === 'HOOK' ? 2.4 : opt.style === 'asmr' ? 1.4 : 0.8);
    const tracks = [...canvas.captureStream(FPS).getVideoTracks(), ...(audioOut ? audioOut.stream.getAudioTracks() : [])];
    let rec;
    try {
      rec = new MediaRecorder(new MediaStream(tracks), { mimeType: mime, videoBitsPerSecond: 10_000_000, audioBitsPerSecond: 128_000 });
    } catch (e) {
      failed(`This browser could not start recording (${e.name || e}). Try Chrome or Safari.`);
      return;
    }
    const chunks = [];
    rec.ondataavailable = (e) => { if (e.data && e.data.size) chunks.push(e.data); };
    rec.onerror = (e) => failed(`Recording failed (${(e.error && e.error.name) || 'error'}). Try again.`);
    const finished = new Promise((resolve) => { rec.onstop = resolve; });

    // the frame from above that becomes the cover picture
    const still = document.createElement('canvas');
    still.width = W; still.height = H;
    let stillTaken = false;

    try { wake = navigator.wakeLock ? await navigator.wakeLock.request('screen') : null; } catch (_) { wake = null; }
    run = { stopped: false, raf: 0, rec };
    const me = run;
    document.addEventListener('visibilitychange', function away() {
      if (document.hidden && run === me) {
        document.removeEventListener('visibilitychange', away);
        failed('The reel stopped because the page went to the background. Keep this page on screen while it films.');
      }
    });

    // countdown, camera live behind it
    for (let n = COUNTDOWN; n > 0; n--) {
      count.textContent = String(n);
      guide.set(0, 0, false);
      await new Promise((r) => setTimeout(r, 1000));
      if (me.stopped) return;
    }
    count.textContent = '';

    const t0 = performance.now();
    let last = null;
    let voiced = false;

    const setRecording = (on) => {
      if (on && rec.state === 'inactive') rec.start(1000);
      else if (on && rec.state === 'paused') rec.resume();
      else if (!on && rec.state === 'recording') rec.pause();
    };

    const tick = (now) => {
      if (me.stopped) return;
      const s = (now - t0) / 1000;
      let phase = 0, into = s;
      while (phase < list.length - 1 && into >= list[phase].len) { into -= list[phase].len; phase++; }
      guide.set(phase, Math.min(into, list[phase].len), true);

      cover(ctx, video, video.videoWidth, video.videoHeight);
      const k = keep.find((x) => s >= x.from && s < x.to);
      if (k && k.kind === 'TOP' && !stillTaken && s - k.from >= 1 && video.videoWidth) {
        cover(still.getContext('2d'), video, video.videoWidth, video.videoHeight);
        stillTaken = true;
      }
      if (k) drawShot(ctx, k, s - k.from);
      setRecording(!!k);
      if (mic && k !== last) mic.gain.setTargetAtTime(level(k), ac.currentTime, 0.015);
      if (ac && k && k !== last && k.kind === 'STAMP') {
        const fx = ac.createBufferSource();
        fx.buffer = sealSound(ac);
        fx.connect(audioOut);
        fx.start(ac.currentTime + Math.max(0, STAMP_HIT - 0.45));
      }
      if (ac && voice && talk() && !voiced && k && k.endingFrom != null && s - k.from >= k.endingFrom) {
        voiced = true;
        const v = ac.createBufferSource();
        v.buffer = voice;
        v.connect(audioOut);
        v.start(ac.currentTime + VOICE_AT);
      }
      last = k;
      if (s >= filmLen || s >= keep[keep.length - 1].to) {
        rec.stop();
        return;
      }
      me.raf = requestAnimationFrame(tick);
    };
    me.raf = requestAnimationFrame(tick);

    await finished;
    if (me.stopped || run !== me) return;
    const type = rec.mimeType || mime;
    const blob = new Blob(chunks, { type: type.split(';')[0] });
    if (!blob.size) { failed('The recording came out empty. Try again.'); return; }
    const coverBlob = stillTaken ? await coverPicture(still) : null;
    done(blob, type, coverBlob);
  }

  function cover(c, src, sw0, sh0) {
    if (!sw0 || !sh0) { c.fillStyle = '#000'; c.fillRect(0, 0, W, H); return; }
    const scale = Math.max(W / sw0, H / sh0);
    const sw = W / scale, sh = H / scale;
    c.drawImage(src, (sw0 - sw) / 2, (sh0 - sh) / 2, sw, sh, 0, 0, W, H);
  }

  // ---------------- overlays (as in the app's ReelOverlays) ----------------
  // Words stay where Instagram and TikTok leave the picture free: below their top bar, above
  // their caption and buttons (y < 1480) and left of the right-hand buttons (x < 920).

  const fade = (t, start = 0, len = 0.3) => Math.min(1, Math.max(0, (t - start) / len));
  const ease = (k) => 1 - (1 - k) * (1 - k);
  const font = (size, mono = false, weight = 'bold') => `${weight} ${size}px ${mono ? MONO : SANS}`;

  function shadow(c, blur, alpha) { c.shadowColor = `rgba(0,0,0,${alpha})`; c.shadowBlur = blur; c.shadowOffsetY = 3; }
  function noShadow(c) { c.shadowColor = 'transparent'; c.shadowBlur = 0; c.shadowOffsetY = 0; }

  /** Sets [c]'s font to [size] or smaller, until [s] fits in [max] pixels. */
  function fit(c, s, size, max, mono = false, weight = 'bold') {
    let z = size;
    c.font = font(z, mono, weight);
    while (c.measureText(s).width > max && z > 24) { z -= 2; c.font = font(z, mono, weight); }
    return z;
  }

  const QUIPS = {
    HOOK: () => 'Shhh… ascolta!', TOP: () => 'Guarda che bella!', UNDER: () => 'Il sotto? Leopardato!', SLICE: () => 'Filante!',
    AROUND: (i) => (i === 0 ? 'Che cornicione!' : 'Gira, gira!'),
  };
  function label(kind, index) {
    switch (kind) {
      case 'TOP': return ["DALL'ALTO", null];
      case 'AROUND': return index === 0
        ? ['TUTTO INTORNO', opt.hyd ? `${opt.hyd}% idratazione` : 'fatta a mano']
        : ['TUTTO INTORNO', opt.ferm ? `${opt.ferm} h di lievitazione` : (opt.bake ? `${clock(opt.bake)} in forno` : 'croccante')];
      case 'UNDER': return ['IL SOTTO', 'leopardato'];
      case 'SLICE': return ['LA FETTA', 'filante'];
      default: return null;
    }
  }

  /** The overlay of shot [k] at [t] seconds into it. */
  function drawShot(c, k, t) {
    if (opt.style === 'clean') return;
    const len = k.to - k.from;
    const out = k.last ? Math.min(1, Math.max(0, (len - t) / LOOP_FADE)) : 1;
    if (out <= 0) return;
    c.save();
    c.globalAlpha = out;
    noShadow(c);
    if (k.kind === 'STAMP') drawStamp(c, t);
    else if (k.endingFrom != null && t >= k.endingFrom) drawEnding(c, t - k.endingFrom);
    else {
      const away = k.endingFrom != null ? 1 - fade(t, k.endingFrom - 0.25, 0.25) : 1;
      c.globalAlpha = out * away;
      if (opt.style === 'stats') {
        if (k.kind === 'HOOK') hookTitle(c, t);
        else {
          const l = label(k.kind, k.index);
          if (l) drawLabel(c, t, l[0], l[1]);
          if (k.kind === 'TOP') chips(c, t, stats());
        }
      } else if (opt.style === 'funny' && QUIPS[k.kind]) {
        chef(c, t, QUIPS[k.kind](k.index));
      }
    }
    c.restore();
  }

  /** The first frame's words: what this is, and a reason to keep the sound on. */
  function hookTitle(c, t) {
    const k = ease(fade(t, 0, 0.2));
    const y = 420 + (1 - k) * 40;
    c.save();
    c.globalAlpha *= k;
    c.textAlign = 'center';
    c.fillStyle = '#fff';
    shadow(c, 12, 0.8);
    fit(c, pizzaTitle(), 92, 900, false, '900');
    c.fillText(pizzaTitle(), W / 2, y);
    c.fillStyle = C.flame;
    fit(c, 'ASCOLTA IL CRUNCH', 46, 900, true);
    c.fillText('ASCOLTA IL CRUNCH', W / 2, y + 78);
    c.restore();
  }

  /** The scene's name, low on the screen, sliding in, with its number in the brand colour. */
  function drawLabel(c, t, title, sub) {
    const k = ease(fade(t, 0.05, 0.3));
    if (k <= 0) return;
    const x = 64 - (1 - k) * 120, y = LABEL_Y;
    c.save();
    c.globalAlpha *= k;
    c.textAlign = 'left';
    fit(c, title, 64, SAFE_RIGHT - 120);
    const w = c.measureText(title).width;
    c.fillStyle = 'rgba(0,0,0,0.69)';
    roundRect(c, x - 28, y - 82, w + 56, 112, 22);
    c.fill();
    c.fillStyle = '#fff';
    c.fillText(title, x, y);
    if (sub) {
      fit(c, sub, 40, SAFE_RIGHT - 84, true);
      c.fillStyle = C.flame;
      shadow(c, 8, 0.8);
      c.fillText(sub, x, y + 82);
    }
    c.restore();
  }

  /** The numbers as chips stacked above the label, one after another. */
  function chips(c, t, items) {
    let y = LABEL_Y - 150 - (items.length - 1) * 92;
    items.forEach((s, i) => {
      const k = ease(fade(t, 0.25 + i * 0.12, 0.25));
      if (k > 0) {
        c.save();
        c.globalAlpha *= k;
        fit(c, s, 42, SAFE_RIGHT - 120, true);
        const w = c.measureText(s).width;
        const x = 64 - (1 - k) * 80;
        c.fillStyle = C.flame;
        roundRect(c, x - 6, y - 56, w + 40, 74, 37);
        c.fill();
        c.fillStyle = C.ink;
        c.textAlign = 'left';
        c.fillText(s, x + 14, y);
        c.restore();
      }
      y += 92;
    });
  }

  /** A white speech bubble centred near ([bx], [by]) with its tail towards ([tx], [ty]). */
  function bubble(c, text, bx, by, tx, ty, size = 54) {
    c.save();
    const z = fit(c, text, size, SAFE_RIGHT - 120);
    const bw = c.measureText(text).width + 76, bh = z * 2.1;
    const left = Math.min(Math.max(40, bx - bw / 2), SAFE_RIGHT - bw);
    const cx = left + bw / 2;
    c.fillStyle = '#fff';
    c.shadowColor = 'rgba(0,0,0,0.5)'; c.shadowBlur = 16; c.shadowOffsetY = 4;
    c.beginPath();
    c.moveTo(cx - bw * 0.12, by + bh / 2 - 6);
    c.lineTo(tx, ty);
    c.lineTo(cx + bw * 0.08, by + bh / 2 - 6);
    c.closePath();
    c.fill();
    roundRect(c, left, by - bh / 2, bw, bh, bh / 2);
    c.fill();
    noShadow(c);
    c.fillStyle = C.ink;
    c.textAlign = 'center';
    c.fillText(text, cx, by + z * 0.36);
    c.restore();
  }

  /** Funny style: the pizzaiolo peeks in at the bottom left and says [text]. */
  function chef(c, t, text) {
    const up = ease(fade(t, 0, 0.3));
    const size = 460, cx = 230;
    const bottom = H - 170 + (1 - up) * 420;
    pizzaiolo(c, cx, bottom, size, t);
    const b = fade(t, 0.2, 0.2);
    if (b > 0) {
      c.save();
      c.globalAlpha *= b;
      const headTop = bottom - size * 0.98;
      bubble(c, text, cx + 300, headTop - 40, cx + 90, headTop + 30);
      c.restore();
    }
  }

  /** The seal falls onto the pizza (on moving footage, slightly darkened) and lands with a bounce. */
  function drawStamp(c, t) {
    c.fillStyle = `rgba(0,0,0,${(0.37 * fade(t, 0, 0.25)).toFixed(3)})`;
    c.fillRect(0, 0, W, H);
    const cx = W / 2, cy = H * 0.44, r = 280;
    let scale, turn, alpha;
    if (t < STAMP_HIT) {
      const e = (t / STAMP_HIT) ** 2;
      scale = 3.2 - 2.2 * e;
      turn = -35 * (1 - e);
      alpha = 0.25 + 0.75 * (t / STAMP_HIT);
    } else {
      const u = Math.min(1, (t - STAMP_HIT) / 0.3);
      scale = 1 + 0.09 * Math.sin(u * Math.PI) * (1 - u);
      turn = 0;
      alpha = 1;
    }
    if (t >= STAMP_HIT && t < STAMP_HIT + 0.55) {
      const u = (t - STAMP_HIT) / 0.55;
      c.beginPath();
      c.arc(cx, cy, r * (1.02 + 0.9 * u), 0, Math.PI * 2);
      c.lineWidth = 34 * (1 - u);
      c.strokeStyle = `rgba(255,244,220,${(0.75 * (1 - u)).toFixed(3)})`;
      c.stroke();
    }
    const s = seal();
    const k = r / SEAL_R;
    c.save();
    c.globalAlpha *= alpha;
    c.translate(cx, cy);
    c.scale(scale * k, scale * k);
    c.rotate((turn * Math.PI) / 180);
    c.drawImage(s.canvas, -s.cx, -s.cy);
    c.restore();
    const a = fade(t, STAMP_HIT + 0.3, 0.35);
    if (a > 0) {
      c.save();
      c.globalAlpha *= a;
      c.fillStyle = '#fff';
      c.textAlign = 'center';
      shadow(c, 10, 0.8);
      const line = `${pizzaTitle()}${opt.bake ? ` · ${clock(opt.bake)} in forno` : ''}`;
      fit(c, line, 48, 880, true);
      c.fillText(line, cx, cy + r * 1.72);
      c.restore();
    }
  }

  // ---------------- the ending ----------------

  /**
   * The ending, over the last of the footage: the pizzaiolo's handle (the reel is theirs), the
   * cartoon pizzaiolo calling the pizza ready, and a small app tag if wanted.
   */
  function drawEnding(c, t) {
    const dim = fade(t, 0, 0.35);
    const g = c.createLinearGradient(0, 0, 0, H);
    g.addColorStop(0, `rgba(0,0,0,${(0.6 * dim).toFixed(3)})`);
    g.addColorStop(0.45, `rgba(0,0,0,${(0.13 * dim).toFixed(3)})`);
    g.addColorStop(1, `rgba(0,0,0,${(0.67 * dim).toFixed(3)})`);
    c.fillStyle = g;
    c.fillRect(0, 0, W, H);
    const cx = W / 2;
    const k = ease(fade(t, 0.05, 0.3));
    const asmr = opt.style === 'asmr';
    c.save();
    c.globalAlpha *= k;
    c.textAlign = 'center';
    shadow(c, 12, 0.8);
    let y = 420;
    const handle = handleText();
    if (handle) {
      c.fillStyle = '#fff';
      fit(c, handle, 84, 880, false, '900');
      c.fillText(handle, cx, y);
      y += 70;
    }
    if (!asmr || !handle) {
      c.fillStyle = handle ? C.flame : '#fff';
      if (handle) fit(c, pizzaTitle(), 44, 880, true); else fit(c, pizzaTitle(), 76, 880, false, '900');
      c.fillText(pizzaTitle(), cx, y);
    }
    y += 60;
    if (opt.tag) {
      c.font = font(32);
      c.fillStyle = C.muted;
      const s = 'made with VibePizzaiolo';
      const w = c.measureText(s).width;
      drawLogo(c, cx - w / 2 - 48, y - 34, 40, C.flame);
      c.fillText(s, cx + 6, y);
    }
    c.restore();
    if (asmr) return;

    // the pizzaiolo slides up and waves; the bubble comes with the voice
    const up = ease(fade(t, 0.1, 0.45));
    const size = 640, pcx = 300;
    const bottom = H - 230 + (1 - up) * 700;
    pizzaiolo(c, pcx, bottom, size, t);
    const b = fade(t, VOICE_AT, 0.2);
    if (b > 0) {
      c.save();
      c.globalAlpha *= b;
      const headTop = bottom - size * 0.98;
      bubble(c, 'La pizza è pronta!', 620, headTop - 10, pcx + 120, headTop + 60, 52);
      c.restore();
    }
  }

  /** The cover for the profile grid: the pizza from above, the title, the seal, the handle. */
  function coverPicture(still) {
    const cv = document.createElement('canvas');
    cv.width = W; cv.height = H;
    const c = cv.getContext('2d');
    c.drawImage(still, 0, 0);
    c.fillStyle = 'rgba(0,0,0,0.27)';
    c.fillRect(0, 0, W, H);
    const cx = W / 2;
    c.textAlign = 'center';
    shadow(c, 12, 0.8);
    c.fillStyle = '#fff';
    fit(c, pizzaTitle(), 96, 940, false, '900');
    c.fillText(pizzaTitle(), cx, 460);
    const sub = stats().slice(0, 2).join(' · ');
    if (sub) {
      c.fillStyle = C.flame;
      fit(c, sub, 46, 900, true);
      c.fillText(sub, cx, 540);
    }
    noShadow(c);
    if (SEALS[opt.seal]) {
      const s = seal();
      const k = 250 / SEAL_R;
      c.save();
      c.translate(cx, H * 0.53);
      c.scale(k, k);
      c.drawImage(s.canvas, -s.cx, -s.cy);
      c.restore();
    }
    if (handleText()) {
      shadow(c, 12, 0.8);
      c.fillStyle = '#fff';
      fit(c, handleText(), 64, 900, false, '900');
      c.fillText(handleText(), cx, 1500);
    }
    return new Promise((resolve) => cv.toBlob((b) => resolve(b), 'image/jpeg', 0.92));
  }

  // ---------------- the logo ----------------

  function roundRect(c, x, y, w, h, r) {
    c.beginPath();
    c.moveTo(x + r, y);
    c.arcTo(x + w, y, x + w, y + h, r);
    c.arcTo(x + w, y + h, x, y + h, r);
    c.arcTo(x, y + h, x, y, r);
    c.arcTo(x, y, x + w, y, r);
    c.closePath();
  }

  // the app icon: an oven dome that is also a stopwatch (the launcher icon's paths)
  const LOGO = [
    'M24,78L24,60A30,30 0,0 1,84,60L84,78Z M41,78L41,66A13,13 0,0 1,67,66L67,78Z M51,47A3,3 0,1 0,57,47A3,3 0,1 0,51,47Z M55.67,43.45L60.95,37.17L62.49,38.45L57.21,44.73Z',
    'M50,19L58,19A2,2 0,0 1,60,21L60,25L48,25L48,21A2,2 0,0 1,50,19Z M52,25L56,25L56,30.5L52,30.5Z',
    'M54,76C49.6,76 47.5,72.7 48.8,69.2C49.7,66.7 52.1,65.6 52.4,62C55,63.6 56,66.1 55.5,68.2C56.5,67.6 57.1,66.6 57.3,65.4C59.3,67.5 59.9,70.4 58.8,72.8C57.9,74.8 56.2,76 54,76Z',
    'M22,80L86,80A2,2 0,0 1,88,82A2,2 0,0 1,86,84L22,84A2,2 0,0 1,20,82A2,2 0,0 1,22,80Z',
  ].map((d) => new Path2D(d));

  function drawLogo(c, x, y, size, color) {
    c.save();
    c.translate(x, y);
    c.scale(size / 72, size / 72);
    c.translate(-18, -16);
    c.fillStyle = color;
    LOGO.forEach((p, i) => c.fill(p, i === 0 ? 'evenodd' : 'nonzero'));
    c.restore();
  }

  // ---------------- the quality seal ----------------

  const SEAL_R = 300;
  let sealImage = null;

  /** The seal drawn once (again when its words change), then dropped onto the pizza as a picture. */
  function seal() {
    const words = SEALS[opt.seal] || SEALS.qualita;
    const key = `${opt.seal}|${opt.n}`;
    if (sealImage && sealImage.key === key) return sealImage;
    const r = SEAL_R;
    const cv = document.createElement('canvas');
    cv.width = Math.round(r * 2.8); cv.height = Math.round(r * 3.0);
    const c = cv.getContext('2d');
    const cx = cv.width / 2, cy = r * 1.3;
    c.translate(cx, cy);
    c.rotate((-10 * Math.PI) / 180);

    // ribbon tails in the Italian colours, behind the seal
    const tailW = r * 0.42, tailH = r * 1.0, tailTop = r * 0.35;
    for (const side of [-1, 1]) {
      c.save();
      c.translate(0, tailTop);
      c.rotate((side * 20 * Math.PI) / 180);
      c.translate(0, -tailTop);
      const left = side * r * 0.3 - tailW / 2;
      c.beginPath();
      c.moveTo(left, tailTop);
      c.lineTo(left + tailW, tailTop);
      c.lineTo(left + tailW, tailTop + tailH);
      c.lineTo(left + tailW / 2, tailTop + tailH - tailW * 0.4);
      c.lineTo(left, tailTop + tailH);
      c.closePath();
      c.clip();
      [C.itGreen, C.itWhite, C.itRed].forEach((col, i) => { c.fillStyle = col; c.fillRect(left + (i * tailW) / 3, tailTop, tailW / 3 + 1, tailH); });
      c.restore();
    }

    const disc = (x, y, rr, col) => { c.beginPath(); c.arc(x, y, rr, 0, Math.PI * 2); c.fillStyle = col; c.fill(); };
    const ring = (rr, w) => { c.beginPath(); c.arc(0, 0, rr, 0, Math.PI * 2); c.lineWidth = w; c.strokeStyle = C.goldDark; c.stroke(); };
    disc(r * 0.03, r * 0.05, r, 'rgba(0,0,0,0.33)');
    for (let i = 0; i < 30; i++) {
      const a = (Math.PI * 2 * i) / 30;
      disc(r * 0.9 * Math.cos(a), r * 0.9 * Math.sin(a), r * 0.115, C.gold);
    }
    disc(0, 0, r * 0.92, C.gold);
    ring(r * 0.86, r * 0.025);
    disc(0, 0, r * 0.6, C.sealRed);
    ring(r * 0.6, r * 0.03);

    // ring text in the middle of the gold band: upright on top, readable from below at the bottom
    const size = r * 0.155, capHalf = size * 0.36, bandMid = r * 0.73;
    c.fillStyle = C.ink;
    arcText(c, 'FATTA A MANO', bandMid - capHalf, size, 0.12, true);
    arcText(c, 'CON AMORE', bandMid + capHalf, size, 0.12, false);
    c.font = font(r * 0.13);
    c.textAlign = 'center';
    c.fillText('★', -bandMid, r * 0.045);
    c.fillText('★', bandMid, r * 0.045);

    c.fillStyle = C.itWhite;
    c.font = font(r * 0.23);
    spaced(c, words.big, 0, -r * 0.04, r * 0.23 * 0.04);
    c.font = font(r * 0.16);
    spaced(c, words.small, 0, r * 0.17, r * 0.16 * 0.06);
    c.fillStyle = C.gold;
    c.font = font(r * 0.13, true);
    c.textAlign = 'center';
    c.fillText(opt.n ? `Nº ${opt.n}` : String(new Date().getFullYear()), 0, r * 0.4);
    sealImage = { canvas: cv, cx, cy, key };
    return sealImage;
  }

  /** Centred text with extra letter spacing (canvas letterSpacing is not everywhere yet). */
  function spaced(c, s, x, y, gap) {
    const widths = [...s].map((ch) => c.measureText(ch).width);
    const total = widths.reduce((a, b) => a + b, 0) + gap * (widths.length - 1);
    let at = x - total / 2;
    c.textAlign = 'left';
    [...s].forEach((ch, i) => { c.fillText(ch, at, y); at += widths[i] + gap; });
  }

  /** Text centred on a half circle of radius [rad] round (0,0), over the top or under the bottom. */
  function arcText(c, s, rad, size, spacing, top) {
    c.font = font(size);
    c.textAlign = 'center';
    const chars = [...s];
    const widths = chars.map((ch) => c.measureText(ch).width + spacing * size);
    const total = widths.reduce((a, b) => a + b, 0) - spacing * size;
    let along = (Math.PI * rad - total) / 2;
    chars.forEach((ch, i) => {
      const mid = along + (widths[i] - spacing * size) / 2;
      const a = top ? Math.PI + mid / rad : Math.PI - mid / rad;
      c.save();
      c.translate(rad * Math.cos(a), rad * Math.sin(a));
      c.rotate(top ? a + Math.PI / 2 : a - Math.PI / 2);
      c.fillText(ch, 0, 0);
      c.restore();
      along += widths[i];
    });
  }

  /** The cartoon pizzaiolo: hat, white jacket, red neckerchief, a peel with a pizza, waving. */
  function pizzaiolo(c, cx, bottom, s, t) {
    const P = {
      outline: '#2A1E16', skin: '#F2C29B', cheek: 'rgba(232,106,90,0.33)', white: '#FBF8F2', shade: '#E3DED3',
      red: '#CE2B37', wood: '#B9824B', crust: '#E2B06E', sauce: '#C6372A', cheese: '#FFF4DC', basil: '#2F8F3A', tache: '#4A2C1A',
    };
    const top = bottom - s;
    const lw = s * 0.012;
    c.save();
    c.lineJoin = 'round';
    c.lineCap = 'round';
    c.strokeStyle = P.outline;
    c.lineWidth = lw;
    const shape = (col) => { c.fillStyle = col; c.fill(); c.stroke(); };
    const circle = (x, y, r) => { c.beginPath(); c.arc(x, y, r, 0, Math.PI * 2); };
    const oval = (l, tp, rt, bt) => { c.beginPath(); c.ellipse((l + rt) / 2, (tp + bt) / 2, (rt - l) / 2, (bt - tp) / 2, 0, 0, Math.PI * 2); };
    const rad = (d) => (d * Math.PI) / 180;

    const shoulderY = top + s * 0.56, headCy = top + s * 0.40, headR = s * 0.165;

    // waving arm, behind the body
    const wave = -150 + 22 * Math.sin(t * 2 * Math.PI * 1.6);
    {
      const x = cx - s * 0.25, y = shoulderY + s * 0.03;
      c.save();
      c.translate(x, y); c.rotate(rad(wave)); c.translate(-x, -y);
      roundRect(c, x - s * 0.045, y, s * 0.09, s * 0.26, s * 0.045);
      shape(P.white);
      circle(x, y + s * 0.28, s * 0.045);
      shape(P.skin);
      c.restore();
    }

    // jacket
    c.beginPath();
    c.moveTo(cx - s * 0.27, shoulderY);
    c.quadraticCurveTo(cx, shoulderY - s * 0.05, cx + s * 0.27, shoulderY);
    c.lineTo(cx + s * 0.31, bottom);
    c.lineTo(cx - s * 0.31, bottom);
    c.closePath();
    shape(P.white);
    c.fillStyle = P.shade;
    c.fillRect(cx - s * 0.29, bottom - s * 0.06, s * 0.58, s * 0.059);
    c.fillStyle = P.outline;
    for (let i = 0; i < 3; i++) {
      const y = shoulderY + s * (0.12 + i * 0.09);
      circle(cx - s * 0.06, y, s * 0.014); c.fill();
      circle(cx + s * 0.06, y, s * 0.014); c.fill();
    }
    // neckerchief
    c.beginPath();
    c.moveTo(cx - s * 0.12, shoulderY - s * 0.02);
    c.lineTo(cx + s * 0.12, shoulderY - s * 0.02);
    c.lineTo(cx, shoulderY + s * 0.1);
    c.closePath();
    shape(P.red);

    // the peel with a pizza, in the other hand
    {
      const bob = Math.sin(t * 2 * Math.PI * 0.8) * s * 0.012;
      const x = cx + s * 0.36, y = shoulderY + s * 0.2 + bob;
      const bx = x + s * 0.12, by = y - s * 0.17;
      const ex = x - (bx - x) * 0.45, ey = y - (by - y) * 0.45;
      c.beginPath(); c.moveTo(bx, by); c.lineTo(ex, ey);
      c.lineWidth = s * 0.036 + lw * 2; c.stroke();
      c.strokeStyle = P.wood; c.lineWidth = s * 0.036; c.stroke();
      c.strokeStyle = P.outline; c.lineWidth = lw;
      oval(bx - s * 0.17, by - s * 0.07, bx + s * 0.17, by + s * 0.07); shape(P.wood);
      oval(bx - s * 0.14, by - s * 0.055, bx + s * 0.14, by + s * 0.045); c.fillStyle = P.crust; c.fill();
      oval(bx - s * 0.115, by - s * 0.04, bx + s * 0.115, by + s * 0.03); c.fillStyle = P.sauce; c.fill();
      c.fillStyle = P.cheese;
      for (const [dx, dy] of [[-0.05, -0.01], [0.04, 0], [-0.01, 0.015], [0.07, -0.02]]) {
        oval(bx + s * (dx - 0.022), by + s * (dy - 0.012), bx + s * (dx + 0.022), by + s * (dy + 0.012)); c.fill();
      }
      oval(bx, by - s * 0.03, bx + s * 0.035, by - s * 0.015); c.fillStyle = P.basil; c.fill();
      circle(x, y, s * 0.045); shape(P.skin);
    }

    // head: neck, face, closed happy eyes, cheeks, smile, moustache
    circle(cx, headCy + s * 0.13, s * 0.06); c.fillStyle = P.skin; c.fill();
    circle(cx, headCy, headR); shape(P.skin);
    c.lineWidth = s * 0.014;
    const arc = (l, tp, rt, bt, from, sweep) => {
      c.beginPath();
      c.ellipse((l + rt) / 2, (tp + bt) / 2, (rt - l) / 2, (bt - tp) / 2, 0, rad(from), rad(from + sweep));
      c.stroke();
    };
    arc(cx - s * 0.095, headCy - s * 0.05, cx - s * 0.035, headCy, 200, 140);
    arc(cx + s * 0.035, headCy - s * 0.05, cx + s * 0.095, headCy, 200, 140);
    c.fillStyle = P.cheek;
    circle(cx - s * 0.1, headCy + s * 0.04, s * 0.03); c.fill();
    circle(cx + s * 0.1, headCy + s * 0.04, s * 0.03); c.fill();
    arc(cx - s * 0.06, headCy + s * 0.02, cx + s * 0.06, headCy + s * 0.11, 20, 140);
    c.lineWidth = lw;
    c.beginPath();
    c.moveTo(cx, headCy + s * 0.035);
    c.bezierCurveTo(cx - s * 0.04, headCy, cx - s * 0.1, headCy + s * 0.03, cx - s * 0.11, headCy);
    c.bezierCurveTo(cx - s * 0.1, headCy + s * 0.07, cx - s * 0.03, headCy + s * 0.07, cx, headCy + s * 0.05);
    c.bezierCurveTo(cx + s * 0.03, headCy + s * 0.07, cx + s * 0.1, headCy + s * 0.07, cx + s * 0.11, headCy);
    c.bezierCurveTo(cx + s * 0.1, headCy + s * 0.03, cx + s * 0.04, headCy, cx, headCy + s * 0.035);
    c.closePath();
    c.fillStyle = P.tache; c.fill();

    // chef's hat: three puffs (only their outer edge inked) and a band
    const bandTop = headCy - headR * 0.95;
    c.beginPath();
    for (const [x, y, r] of [[cx - s * 0.085, bandTop - s * 0.07, s * 0.085], [cx + s * 0.085, bandTop - s * 0.07, s * 0.085], [cx, bandTop - s * 0.12, s * 0.1]]) {
      c.moveTo(x + r, y);
      c.arc(x, y, r, 0, Math.PI * 2);
    }
    c.lineWidth = lw * 2; c.stroke();
    c.fillStyle = P.white; c.fill();
    c.lineWidth = lw;
    roundRect(c, cx - s * 0.13, bandTop - s * 0.035, s * 0.26, s * 0.07, s * 0.02);
    shape(P.white);
    c.restore();
  }

  // ---------------- sound: the whoosh and the thud of the seal ----------------

  function sealSound(ctx) {
    const rate = ctx.sampleRate;
    const buf = ctx.createBuffer(1, Math.round(rate * 1.0), rate);
    const pcm = buf.getChannelData(0);
    let seed = 7;
    const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
    // whoosh as the seal comes down: filtered noise, rising
    const wLen = Math.round(0.45 * rate);
    let lp = 0;
    for (let i = 0; i < wLen; i++) {
      const k = i / wLen;
      lp += (0.08 + 0.5 * k) * ((rnd() * 2 - 1) - lp);
      pcm[i] += lp * (0.05 + 0.6 * k) * 0.5;
    }
    // the thud: a low falling sine and a short knock
    for (let i = 0; i < Math.round(0.5 * rate) && wLen + i < pcm.length; i++) {
      const t = i / rate;
      const freq = 95 * Math.exp(-t * 3) + 45;
      const body = Math.sin(2 * Math.PI * freq * t) * Math.exp(-t * 9);
      const knock = (rnd() * 2 - 1) * Math.exp(-t * 70);
      pcm[wLen + i] += Math.max(-1, Math.min(1, 0.85 * body + 0.35 * knock));
    }
    return buf;
  }

  start();
})();
