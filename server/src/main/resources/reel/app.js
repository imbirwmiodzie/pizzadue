'use strict';
/*
 * The pizza reel in the browser. The page films with the phone's own camera at 1080p / 30 fps
 * (never HDR) and edits while it films: a canvas gets every camera frame with the reel's labels
 * drawn on, and the recorder only runs during the seconds that go into the reel. After the
 * filming the quality seal and the ending with the pizzaiolo are drawn onto the same canvas,
 * so the reel is ready a few seconds after the last shot. Nothing leaves the phone.
 *
 * The drawing follows the Android app (VideoMaker, CardRenderer.qualitySeal, Pizzaiolo, Soundtrack).
 * Optional link parameters with the real bake: ?n=<pizza number>&bake=<bake time in seconds>.
 */
(() => {
  const W = 1080, H = 1920, FPS = 30;
  const COUNTDOWN = 3;
  const STAMP = 2.6, STAMP_HIT = 0.55, OUTRO = 3.8, VOICE_AT = 0.7;

  // the guide: what to film, and for how long (seconds of filming)
  const PHASES = [
    { title: "DALL'ALTO", say: 'Hold the phone flat above the pizza.', len: 3 },
    { title: 'TUTTO INTORNO', say: 'Go slowly once around it, low, at the height of the rim.', len: 9 },
    { title: 'IL CORNICIONE', say: 'Come close to the rim and press it with a finger. Let us hear the crunch!', len: 5 },
  ];
  const FILM = PHASES.reduce((s, p) => s + p.len, 0);
  // the seconds of filming that go into the reel; only the rim keeps its sound
  const KEEP = [
    { from: 0.4, to: 2.4, part: 'top' },
    { from: 4.5, to: 6.25, part: 'around' },
    { from: 8.75, to: 10.5, part: 'around' },
    { from: 12.8, to: 16.8, part: 'rim', sound: true },
  ];
  const STILL_AT = 1.4; // the frame from above behind the seal

  const C = {
    flame: '#FFB020', ink: '#1A1E23', fg: '#F3EFE6', muted: '#9AA3AD',
    gold: '#E9A825', goldDark: '#8A5A10', sealRed: '#B71C1C',
    itGreen: '#009246', itWhite: '#F8F5EE', itRed: '#CE2B37',
  };
  const SANS = 'system-ui, -apple-system, "Segoe UI", Roboto, sans-serif';
  const MONO = 'ui-monospace, "SF Mono", Menlo, "Roboto Mono", monospace';

  const q = new URLSearchParams(location.search);
  const pizzaNo = /^\d{1,5}$/.test(q.get('n') || '') ? Number(q.get('n')) : null;
  const bakeSec = /^\d{1,4}$/.test(q.get('bake') || '') ? Number(q.get('bake')) : null;
  const clock = (s) => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;

  const app = document.getElementById('app');
  const el = (tag, attrs = {}, ...kids) => {
    const e = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs)) {
      if (k === 'class') e.className = v;
      else if (k.startsWith('on')) e.addEventListener(k.slice(2), v);
      else e.setAttribute(k, v);
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
    const steps = el('ol', { class: 'steps' }, ...PHASES.map((ph, i) => el('li', {},
      el('span', { class: 'n' }, String(i + 1)),
      el('div', {}, el('div', { class: 'name' }, ph.title), el('div', { class: 'sub' }, ph.say)),
      el('span', { class: 'secs' }, `${ph.len} s`))));
    show(el('div', { class: 'page' },
      el('div', {},
        el('div', { class: 'brand' }, 'VibePizzaiolo'),
        el('h1', {}, 'Pizza reel'),
        el('p', { class: 'lead' }, `Film your pizza for ${FILM} seconds and get a vertical reel with the quality seal, ready for Instagram, TikTok or WhatsApp.`)),
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

  function done(blob, type) {
    stopAll();
    const ext = type.includes('mp4') ? 'mp4' : 'webm';
    const name = `vibepizzaiolo-reel.${ext}`;
    const url = URL.createObjectURL(blob);
    const file = new File([blob], name, { type: type.split(';')[0] });
    const canShare = !!(navigator.canShare && navigator.canShare({ files: [file] }));
    const share = async () => {
      try { await navigator.share({ files: [file], title: 'La pizza è pronta!', text: '#VibePizzaiolo' }); } catch (_) { /* closed */ }
    };
    show(el('div', { class: 'page' },
      el('div', {}, el('div', { class: 'brand' }, 'VibePizzaiolo'), el('h1', {}, 'La pizza è pronta!')),
      el('video', { class: 'result', src: url, controls: '', playsinline: '', preload: 'auto' }),
      ext === 'webm' ? el('p', { class: 'fine' }, 'This browser saves the reel as WebM, which Instagram may not take. Chrome on Android and Safari on iPhone save MP4.') : null,
      el('div', { class: 'actions' },
        canShare ? el('button', { class: 'btn', onclick: share }, 'Share the reel') : null,
        el('a', { class: canShare ? 'btn ghost' : 'btn', href: url, download: name }, 'Save the video'),
        el('button', { class: 'btn ghost', onclick: () => { URL.revokeObjectURL(url); openCamera(); } }, 'Film again'))));
  }

  // ---------------- camera ----------------

  let cam = null;      // the camera's MediaStream
  let ac = null;       // AudioContext for the crunch, the thud and the voice
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
    const video = el('video', { playsinline: '', muted: '', autoplay: '' });
    video.muted = true;
    video.srcObject = cam;
    const canvas = el('canvas', { width: String(W), height: String(H) });
    const guide = guideBox();
    guide.set(0, 0, false);
    const count = el('div', { class: 'count' });
    const shutter = el('button', { class: 'shutter', 'aria-label': 'Start filming' });
    const close = el('button', { class: 'close', 'aria-label': 'Close', onclick: () => { stopAll(); start(); } }, '✕');
    const stage = el('div', { class: 'stage' }, canvas, video, guide.box, count, el('div', { class: 'bottom' }, shutter), close);
    show(el('div', { class: 'stage-wrap' }, stage));
    try { await video.play(); } catch (_) { /* autoplay muted works everywhere it matters */ }
    shutter.addEventListener('click', () => {
      shutter.remove();
      if (ac) ac.resume().catch(() => {});
      film({ video, canvas, stage, guide, count });
    }, { once: true });
  }

  function guideBox() {
    const title = el('span', {});
    const left = el('span', { class: 'left' });
    const rec = el('span', { class: 'rec' });
    const say = el('div', { class: 'say' });
    const bars = PHASES.map((p) => { const b = el('span', {}, el('i')); b.style.flex = String(p.len); return b; });
    const box = el('div', { class: 'guide' }, el('div', { class: 'title' }, rec, title, left), say, el('div', { class: 'bars' }, ...bars));
    return {
      box,
      set(phase, secs, filming) {
        const p = PHASES[phase];
        rec.style.display = filming ? '' : 'none';
        title.textContent = filming ? p.title : `First: ${p.title}`;
        say.textContent = p.say;
        left.textContent = filming ? `${Math.ceil(p.len - secs)}` : '';
        bars.forEach((b, i) => { b.firstChild.style.width = `${i < phase ? 100 : i === phase ? (100 * secs) / p.len : 0}%`; });
      },
      text(t, s) { rec.style.display = 'none'; title.textContent = t; say.textContent = s; left.textContent = ''; },
    };
  }

  // ---------------- filming and editing ----------------

  async function film({ video, canvas, stage, guide, count }) {
    const ctx = canvas.getContext('2d');
    const mime = pickMime();
    const audioOut = ac ? ac.createMediaStreamDestination() : null;
    let crunch = null;
    if (ac && cam.getAudioTracks().length) {
      crunch = ac.createGain();
      crunch.gain.value = 0;
      ac.createMediaStreamSource(cam).connect(crunch).connect(audioOut);
    }
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
    let lastKeep = null;
    let stillsAt = 0;

    const setRecording = (on) => {
      if (on && rec.state === 'inactive') rec.start(1000);
      else if (on && rec.state === 'paused') rec.resume();
      else if (!on && rec.state === 'recording') rec.pause();
    };

    const tick = (now) => {
      if (me.stopped) return;
      const s = (now - t0) / 1000;
      if (!stillsAt) {
        // ---- filming ----
        let phase = 0, into = s;
        while (phase < PHASES.length - 1 && into >= PHASES[phase].len) { into -= PHASES[phase].len; phase++; }
        guide.set(phase, Math.min(into, PHASES[phase].len), true);

        cover(ctx, video, video.videoWidth, video.videoHeight);
        if (!stillTaken && s >= STILL_AT && video.videoWidth) {
          const sc = still.getContext('2d');
          cover(sc, video, video.videoWidth, video.videoHeight);
          sc.fillStyle = 'rgba(0,0,0,0.37)';
          sc.fillRect(0, 0, W, H);
          stillTaken = true;
        }
        const k = KEEP.find((x) => s >= x.from && s < x.to);
        if (k) drawPart(ctx, k.part, s - k.from);
        setRecording(!!k);
        if (crunch && (k && k.sound) !== (lastKeep && lastKeep.sound)) crunch.gain.setTargetAtTime(k && k.sound ? 1 : 0, ac.currentTime, 0.015);
        lastKeep = k;

        if (s >= FILM) {
          // ---- the seal and the ending ----
          cam.getVideoTracks().forEach((t) => t.stop());
          stage.classList.add('rendering');
          guide.box.remove();
          stage.append(el('div', { class: 'making' }, 'Adding the seal and the ending…'));
          stillsAt = now;
          setRecording(true);
          if (ac) {
            const at = ac.currentTime;
            const fx = ac.createBufferSource();
            fx.buffer = sealSound(ac);
            fx.connect(audioOut);
            fx.start(at + STAMP_HIT - 0.45);
            if (voice) {
              const v = ac.createBufferSource();
              v.buffer = voice;
              v.connect(audioOut);
              v.start(at + STAMP + VOICE_AT);
            }
          }
        }
      }
      if (stillsAt) {
        const t = (now - stillsAt) / 1000;
        if (t < STAMP) {
          ctx.drawImage(still, 0, 0);
          drawStamp(ctx, t);
        } else {
          drawOutro(ctx, Math.min(t - STAMP, OUTRO));
        }
        if (t >= STAMP + OUTRO) {
          rec.stop();
          return;
        }
      }
      me.raf = requestAnimationFrame(tick);
    };
    me.raf = requestAnimationFrame(tick);

    await finished;
    if (me.stopped || run !== me) return;
    const type = rec.mimeType || mime;
    const blob = new Blob(chunks, { type: type.split(';')[0] });
    if (!blob.size) { failed('The recording came out empty. Try again.'); return; }
    done(blob, type);
  }

  function cover(c, src, sw0, sh0) {
    if (!sw0 || !sh0) { c.fillStyle = '#000'; c.fillRect(0, 0, W, H); return; }
    const scale = Math.max(W / sw0, H / sh0);
    const sw = W / scale, sh = H / scale;
    c.drawImage(src, (sw0 - sw) / 2, (sh0 - sh) / 2, sw, sh, 0, 0, W, H);
  }

  // ---------------- overlays (as in the app's VideoMaker) ----------------

  const fade = (t, start = 0, len = 0.3) => Math.min(1, Math.max(0, (t - start) / len));
  const font = (size, mono = false) => `bold ${size}px ${mono ? MONO : SANS}`;

  function drawPart(c, part, t) {
    brand(c, t);
    if (part === 'top') label(c, t, "DALL'ALTO", pizzaNo ? `Pizza nº ${pizzaNo}` : 'fatta in casa');
    else if (part === 'around') label(c, t, 'TUTTO INTORNO', null);
    else label(c, t, 'IL CORNICIONE', bakeSec ? `${clock(bakeSec)} in forno · croccante` : 'croccante');
  }

  function shadow(c, blur, alpha) { c.shadowColor = `rgba(0,0,0,${alpha})`; c.shadowBlur = blur; c.shadowOffsetY = 2; }
  function noShadow(c) { c.shadowColor = 'transparent'; c.shadowBlur = 0; c.shadowOffsetY = 0; }

  function brand(c, t) {
    c.save();
    c.globalAlpha = fade(t);
    drawLogo(c, 64, 262, 70, C.flame);
    c.font = font(50);
    c.fillStyle = '#fff';
    c.textAlign = 'left';
    shadow(c, 8, 0.6);
    c.fillText('VibePizzaiolo', 150, 318);
    c.restore();
  }

  function label(c, t, title, sub) {
    const k = fade(t, 0.1, 0.35);
    if (k <= 0) return;
    const x = 64 - (1 - k) * 120, y = H - 470;
    c.save();
    c.globalAlpha = k;
    c.font = font(64);
    const w = c.measureText(title).width;
    c.fillStyle = 'rgba(0,0,0,0.69)';
    roundRect(c, x - 28, y - 82, w + 56, 112, 22);
    c.fill();
    c.fillStyle = '#fff';
    c.textAlign = 'left';
    c.fillText(title, x, y);
    if (sub) {
      c.font = font(36, true);
      c.fillStyle = C.flame;
      shadow(c, 8, 0.8);
      c.fillText(sub, x, y + 76);
    }
    c.restore();
  }

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

  /** The seal drawn once, then dropped onto the pizza as a picture. */
  function seal() {
    if (sealImage) return sealImage;
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
    spaced(c, 'QUALITÀ', 0, -r * 0.04, r * 0.23 * 0.04);
    c.font = font(r * 0.16);
    spaced(c, 'SUPERIORE', 0, r * 0.17, r * 0.16 * 0.06);
    c.fillStyle = C.gold;
    c.font = font(r * 0.13, true);
    c.textAlign = 'center';
    c.fillText(pizzaNo ? `Nº ${pizzaNo}` : String(new Date().getFullYear()), 0, r * 0.4);
    sealImage = { canvas: cv, cx, cy };
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

  /** The seal falls onto the pizza and lands with a bounce. */
  function drawStamp(c, t) {
    const cx = W / 2, cy = H * 0.46, r = SEAL_R;
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
    c.save();
    c.globalAlpha = alpha;
    c.translate(cx, cy);
    c.scale(scale, scale);
    c.rotate((turn * Math.PI) / 180);
    c.drawImage(s.canvas, -s.cx, -s.cy);
    c.restore();
    const k = fade(t, STAMP_HIT + 0.35, 0.4);
    if (k > 0) {
      c.save();
      c.globalAlpha = k;
      c.font = font(46, true);
      c.fillStyle = '#fff';
      c.textAlign = 'center';
      shadow(c, 10, 0.8);
      const line = pizzaNo
        ? `Pizza nº ${pizzaNo}${bakeSec ? ` · ${clock(bakeSec)} in forno` : ''}`
        : bakeSec ? `${clock(bakeSec)} in forno` : 'fatta a mano';
      c.fillText(line, cx, cy + r * 1.75);
      c.restore();
    }
  }

  // ---------------- the ending ----------------

  let outroBg = null;
  function drawOutro(c, t) {
    if (!outroBg) {
      outroBg = document.createElement('canvas');
      outroBg.width = W; outroBg.height = H;
      const b = outroBg.getContext('2d');
      b.fillStyle = C.ink;
      b.fillRect(0, 0, W, H);
      const g = b.createRadialGradient(W / 2, H * 0.62, 0, W / 2, H * 0.62, H * 0.6);
      g.addColorStop(0, '#4A2A14');
      g.addColorStop(1, C.ink);
      b.fillStyle = g;
      b.fillRect(0, 0, W, H);
    }
    c.drawImage(outroBg, 0, 0);
    const cx = W / 2;
    noShadow(c);

    c.save();
    c.globalAlpha = fade(t, 0, 0.4);
    drawLogo(c, cx - 70, 270, 140, C.flame);
    c.textAlign = 'center';
    c.fillStyle = C.fg;
    c.font = font(104);
    c.fillText('VibePizzaiolo', cx, 520);
    c.fillStyle = C.flame;
    c.font = font(38, true);
    c.fillText('The voice pizza timer · say “vai!”', cx, 590);
    c.restore();

    // the pizzaiolo slides up and waves
    const f = fade(t, 0.15, 0.55);
    const up = 1 - (1 - (1 - f) * (1 - f));
    pizzaiolo(c, cx - 40, H - 300 + up * 900, 880, t);

    // speech bubble, timed with the voice
    const b = fade(t, VOICE_AT, 0.25);
    if (b > 0) {
      const text = 'La pizza è pronta!';
      c.save();
      c.globalAlpha = b;
      c.font = font(52);
      const bw = c.measureText(text).width + 70;
      const bx = W - 40 - bw / 2, by = 690;
      roundRect(c, bx - bw / 2, by - 70, bw, 110, 40);
      c.moveTo(bx - bw * 0.25, by + 30);
      c.lineTo(bx - bw * 0.42, by + 110);
      c.lineTo(bx - bw * 0.05, by + 30);
      c.closePath();
      c.fillStyle = '#fff';
      c.fill();
      c.fillStyle = C.ink;
      c.textAlign = 'center';
      c.fillText(text, bx, by);
      c.restore();
    }

    const e = fade(t, 1.2, 0.4);
    if (e > 0) {
      c.save();
      c.globalAlpha = e;
      c.textAlign = 'center';
      c.font = font(36, true);
      c.fillStyle = C.fg;
      c.fillText(pizzaNo ? `Pizza nº ${pizzaNo} · fatta a mano con amore` : 'Fatta a mano con amore', cx, H - 230);
      c.font = font(34, true);
      c.fillStyle = C.muted;
      c.fillText('Get the app: link in the post', cx, H - 175);
      c.restore();
    }
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
