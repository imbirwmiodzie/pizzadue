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
    pizzaiolo(c, cx - 40, H - 300 + up * 900, 840, t); // the toque clears the speech bubble

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

  /**
   * The pizzaiolo in pen and ink, as the app draws him (Pizzaiolo.kt): brown ink lines put down
   * twice like a quick sketch, hatched shadows, flat colour a touch off the line. Toque, jacket,
   * knotted neckerchief, a peel with a pizza in one hand, waving with the other.
   */
  function pizzaiolo(c, cx, bottom, s, t) {
    const P = {
      ink: '#2A1E16', white: '#FBF7EE', shade: '#E9E1D2', skin: '#F0C7A4', cheek: 'rgba(232,106,90,0.28)', red: '#C8323A',
      wood: '#C08A55', crust: '#E3AE68', sauce: '#C4402F', cheese: '#FFF6E0', basil: '#3E8C3F', tache: '#4A2C1A',
    };
    const PI = Math.PI, rad = (d) => (d * PI) / 180;
    const k = penKit(c, s, 11, P.ink);
    const top = bottom - s, HS = 0.88;
    const headCy = top + s * 0.36, hrx = s * 0.128, hry = s * 0.152, shoulderY = top + s * 0.56;
    c.save();

    // waving arm, behind the body: shoulder, elbow out, the forearm swings
    const sw = Math.sin(t * 2 * PI * 1.6);
    const sh = [cx - s * 0.176, shoulderY + s * 0.05], el = [cx - s * 0.2746, shoulderY];
    const fa = rad(-96 + 14 * sw), hand = [el[0] + Math.cos(fa) * s * 0.18, el[1] + Math.sin(fa) * s * 0.18];
    const arm = penLimb(sh, [el[0] + s * 0.02, el[1] + s * 0.06], el, s * 0.09, s * 0.078);
    const fore = penLimb(el, [(el[0] + hand[0]) / 2, (el[1] + hand[1]) / 2], hand, s * 0.078, s * 0.068);
    k.shape(fore.outline, P.white); k.shadow(fore.outline, (x) => (x - (el[0] + hand[0]) / 2) / (s * 0.03) + 0.5);
    k.shape(arm.outline, P.white); k.shadow(arm.outline, (x, y) => (y - el[1] - s * 0.01) / (s * 0.03));
    k.stroke(PB().M(...fore.end[0]).Q((fore.end[0][0] + fore.end[1][0]) / 2, (fore.end[0][1] + fore.end[1][1]) / 2 + s * 0.012, ...fore.end[1]).pts, { w: 0.8 });
    {
      const x = hand[0], y = hand[1] - s * 0.045, rot = fa + PI / 2, cr = Math.cos(rot), sr = Math.sin(rot);
      const T = (dx, dy) => [x + dx * s * cr - dy * s * sr, y + dx * s * sr + dy * s * cr];
      const pb = PB().M(...T(-0.03, 0.03));
      for (const q of [[-0.042, 0, -0.04, -0.03, -0.034, -0.045], [-0.036, -0.07, -0.02, -0.075, -0.017, -0.052], [-0.016, -0.08, 0, -0.082, 0, -0.055],
        [0.002, -0.079, 0.019, -0.077, 0.017, -0.05], [0.022, -0.068, 0.036, -0.062, 0.032, -0.04], [0.034, -0.025, 0.036, -0.012, 0.04, -0.012],
        [0.056, -0.024, 0.068, -0.012, 0.054, 0.004], [0.044, 0.016, 0.036, 0.03, 0.028, 0.032]]) pb.C(...T(q[0], q[1]), ...T(q[2], q[3]), ...T(q[4], q[5]));
      pb.L(...T(-0.03, 0.03));
      k.shape(pb.pts, P.skin, { w: 0.85 });
    }

    // jacket
    const body = PB().M(cx - s * 0.0704, shoulderY - s * 0.045)
      .C(cx - s * 0.1408, shoulderY - s * 0.03, cx - s * 0.1971, shoulderY - s * 0.01, cx - s * 0.2182, shoulderY + s * 0.07)
      .C(cx - s * 0.2358, shoulderY + s * 0.17, cx - s * 0.2323, bottom - s * 0.1, cx - s * 0.2394, bottom)
      .L(cx + s * 0.2394, bottom)
      .C(cx + s * 0.2323, bottom - s * 0.1, cx + s * 0.2358, shoulderY + s * 0.17, cx + s * 0.2182, shoulderY + s * 0.07)
      .C(cx + s * 0.1971, shoulderY - s * 0.01, cx + s * 0.1408, shoulderY - s * 0.03, cx + s * 0.0704, shoulderY - s * 0.045).pts;
    k.fill(body, P.white);
    k.shadow(body, (x, y) => (x - (cx + s * 0.1197)) / (s * 0.02) + (y - shoulderY) / (s * 0.3));
    k.shadow(body, (x, y) => (y - (bottom - s * 0.045)) / (s * 0.01), { angle: 20, spacing: 0.009, alpha: 0.5 });
    k.stroke(body);
    k.stroke(PB().M(cx + s * 0.0493, shoulderY + s * 0.03).C(cx + s * 0.0704, shoulderY + s * 0.1, cx + s * 0.0422, shoulderY + s * 0.2, cx + s * 0.0528, bottom).pts, { w: 0.8 });
    for (let i = 0; i < 3; i++) {
      const y = shoulderY + s * (0.1 + i * 0.085);
      for (const bx of [cx - s * 0.0352, cx + s * 0.1091]) k.shape(penEllipse(bx, y, s * 0.014, s * 0.013), P.shade, { w: 0.6 });
    }
    k.stroke(PB().M(cx - s * 0.1408, shoulderY + s * 0.12).Q(cx - s * 0.1197, shoulderY + s * 0.2, cx - s * 0.1408, shoulderY + s * 0.27).pts, { w: 0.55, alpha: 0.8 });
    k.stroke(PB().M(cx + s * 0.176, bottom - s * 0.02).Q(cx + s * 0.1549, bottom - s * 0.08, cx + s * 0.169, bottom - s * 0.13).pts, { w: 0.5, alpha: 0.7 });

    // neck, collar, neckerchief
    const chin = headCy + hry * HS * 0.6;
    const neck = PB().M(cx - s * 0.05, chin).L(cx - s * 0.06, shoulderY - s * 0.01).L(cx + s * 0.06, shoulderY - s * 0.01).L(cx + s * 0.05, chin).pts;
    k.shape(neck, P.skin);
    k.shadow(neck, (x, y) => (headCy + hry * HS * 0.98 - y) / (s * 0.01) + 0.6, { angle: 10, spacing: 0.008 });
    k.shape(PB().M(cx - s * 0.11, shoulderY - s * 0.045).Q(cx, shoulderY, cx + s * 0.11, shoulderY - s * 0.045)
      .L(cx + s * 0.1, shoulderY - s * 0.075).Q(cx, shoulderY - s * 0.035, cx - s * 0.1, shoulderY - s * 0.075).pts, P.white, { w: 0.85 });
    const tailL = PB().M(cx - s * 0.02, shoulderY - s * 0.02).Q(cx - s * 0.06, shoulderY + s * 0.06, cx - s * 0.075, shoulderY + s * 0.115)
      .L(cx - s * 0.025, shoulderY + s * 0.1).Q(cx - s * 0.01, shoulderY + s * 0.04, cx + s * 0.005, shoulderY - s * 0.01).pts;
    const tailR = PB().M(cx + s * 0.02, shoulderY - s * 0.02).Q(cx + s * 0.045, shoulderY + s * 0.05, cx + s * 0.06, shoulderY + s * 0.1)
      .L(cx + s * 0.015, shoulderY + s * 0.085).Q(cx + s * 0.005, shoulderY + s * 0.04, cx - s * 0.005, shoulderY - s * 0.01).pts;
    k.shape(tailL, P.red, { w: 0.85 }); k.shape(tailR, P.red, { w: 0.85 });
    k.shadow(tailR, () => 1, { angle: 75, spacing: 0.01, alpha: 0.6 });
    k.shape(penEllipse(cx, shoulderY - s * 0.022, s * 0.032, s * 0.026), P.red, { w: 0.85 });

    // the arm with the peel and a margherita
    const bob = Math.sin(t * 2 * PI * 0.8) * s * 0.01;
    const sh2 = [cx + s * 0.1901, shoulderY + s * 0.06], el2 = [cx + s * 0.2464, shoulderY + s * 0.25 + bob], hand2 = [cx + s * 0.2886, shoulderY + s * 0.13 + bob];
    {
      const [hx, hy] = hand2, bx = hx + s * 0.07, by = hy - s * 0.21, ang = Math.atan2(by - hy, bx - hx);
      const ex = hx - Math.cos(ang) * s * 0.07, ey = hy - Math.sin(ang) * s * 0.07;
      k.shape(penLimb([ex, ey], [(ex + bx) / 2, (ey + by) / 2], [bx - Math.cos(ang) * s * 0.1, by - Math.sin(ang) * s * 0.1], s * 0.026, s * 0.03).outline, P.wood, { w: 0.8 });
      const blade = PB().M(bx - s * 0.16, by + s * 0.02)
        .C(bx - s * 0.18, by - s * 0.06, bx - s * 0.1, by - s * 0.085, bx, by - s * 0.085)
        .C(bx + s * 0.1, by - s * 0.085, bx + s * 0.18, by - s * 0.06, bx + s * 0.16, by + s * 0.02)
        .C(bx + s * 0.14, by + s * 0.07, bx + s * 0.06, by + s * 0.06, bx + s * 0.02, by + s * 0.075)
        .L(bx - s * 0.02, by + s * 0.075)
        .C(bx - s * 0.06, by + s * 0.06, bx - s * 0.14, by + s * 0.07, bx - s * 0.16, by + s * 0.02).pts;
      k.shape(blade, P.wood);
      k.shadow(blade, (x, y) => (y - (by + s * 0.03)) / (s * 0.015), { angle: 15, spacing: 0.01 });
      k.shape(penEllipse(bx, by - s * 0.012, s * 0.135, s * 0.06, rad(-3)), P.crust, { w: 0.8 });
      for (const [dx, dy] of [[-0.11, -0.03], [-0.04, -0.062], [0.06, -0.06], [0.12, -0.02], [0.08, 0.035], [-0.09, 0.03]]) {
        k.stroke(penEllipse(bx + s * dx, by - s * 0.012 + s * dy * 0.8, s * 0.008, s * 0.004), { w: 0.4, alpha: 0.7 });
      }
      k.fill(penEllipse(bx, by - s * 0.014, s * 0.11, s * 0.045, rad(-3)), P.sauce);
      for (const [dx, dy, r] of [[-0.06, -0.01, 0.024], [0.03, -0.02, 0.022], [-0.01, 0.018, 0.02], [0.07, 0.008, 0.018]]) {
        const m = penEllipse(bx + s * dx, by - s * 0.014 + s * dy, s * r, s * r * 0.55);
        k.fill(m, P.cheese, { exact: true }); k.stroke(m.slice(5, 40), { w: 0.35, alpha: 0.5 });
      }
      for (const [dx, dy, a] of [[-0.025, -0.022, -20], [0.05, 0.015, 30], [-0.075, 0.016, 10]]) {
        const leaf = penEllipse(bx + s * dx, by - s * 0.014 + s * dy, s * 0.016, s * 0.008, rad(a));
        k.fill(leaf, P.basil, { exact: true }); k.stroke(leaf, { w: 0.35, alpha: 0.6 });
      }
    }
    const arm2 = penLimb(sh2, [el2[0] - s * 0.01, el2[1] - s * 0.06], el2, s * 0.09, s * 0.078);
    const fore2 = penLimb(el2, [(el2[0] + hand2[0]) / 2 + s * 0.01, (el2[1] + hand2[1]) / 2 + s * 0.01], [hand2[0] - s * 0.012, hand2[1] + s * 0.03], s * 0.078, s * 0.068);
    k.shape(arm2.outline, P.white); k.shadow(arm2.outline, (x) => (x - (sh2[0] + s * 0.06)) / (s * 0.02));
    k.shape(fore2.outline, P.white); k.shadow(fore2.outline, (x, y) => (y - (el2[1] - s * 0.04)) / (s * 0.02));
    k.stroke(PB().M(...fore2.end[0]).Q((fore2.end[0][0] + fore2.end[1][0]) / 2 + s * 0.008, (fore2.end[0][1] + fore2.end[1][1]) / 2, ...fore2.end[1]).pts, { w: 0.8 });
    k.shape(penEllipse(hand2[0], hand2[1], s * 0.042, s * 0.036, rad(-30)), P.skin);
    for (let i = 0; i < 3; i++) {
      const dx = i * s * 0.014, dy = i * s * 0.008;
      k.stroke(PB().M(hand2[0] - s * 0.022 + dx, hand2[1] - s * 0.03 + dy).Q(hand2[0] - s * 0.005 + dx, hand2[1] - s * 0.012 + dy, hand2[0] - s * 0.012 + dx, hand2[1] + s * 0.004 + dy).pts, { w: 0.45 });
    }

    // head and toque, a little smaller than the body so the figure reads tall
    c.save();
    c.translate(cx, headCy); c.scale(HS, HS); c.translate(-cx, -headCy);
    const kh = penKit(c, s / HS, 41, P.ink);
    const head = [];
    for (let i = 0; i <= 90; i++) {
      const a = PI / 2 + (i / 90) * PI * 2, dy = Math.sin(a), r = hrx * (dy > 0 ? 1 - 0.18 * dy * dy : 1);
      head.push([cx + Math.cos(a) * r, headCy + dy * hry]);
    }
    kh.shape(head, P.skin);
    // closed happy eyes, brows, cheeks, nose, moustache, smile
    for (const sg of [-1, 1]) {
      kh.stroke(PB().M(cx + sg * s * 0.075, headCy - s * 0.035).Q(cx + sg * s * 0.05, headCy - s * 0.058, cx + sg * s * 0.025, headCy - s * 0.035).pts, { w: 1.1 });
      kh.stroke(PB().M(cx + sg * s * 0.085, headCy - s * 0.075).Q(cx + sg * s * 0.055, headCy - s * 0.09, cx + sg * s * 0.02, headCy - s * 0.08).pts, { w: 1.2 });
      c.fillStyle = P.cheek; c.beginPath(); c.ellipse(cx + sg * s * 0.075, headCy + s * 0.012, s * 0.025, s * 0.016, 0, 0, PI * 2); c.fill();
    }
    kh.stroke(PB().M(cx - s * 0.005, headCy - s * 0.03).Q(cx - s * 0.02, headCy + s * 0.012, cx + s * 0.01, headCy + s * 0.018).pts, { w: 0.8 });
    kh.shape(PB().M(cx, headCy + s * 0.03)
      .C(cx - s * 0.03, headCy + s * 0.012, cx - s * 0.085, headCy + s * 0.02, cx - s * 0.1, headCy)
      .C(cx - s * 0.095, headCy + s * 0.06, cx - s * 0.03, headCy + s * 0.06, cx, headCy + s * 0.045)
      .C(cx + s * 0.03, headCy + s * 0.06, cx + s * 0.095, headCy + s * 0.06, cx + s * 0.1, headCy)
      .C(cx + s * 0.085, headCy + s * 0.02, cx + s * 0.03, headCy + s * 0.012, cx, headCy + s * 0.03).pts, P.tache, { w: 0.7 });
    kh.stroke(PB().M(cx - s * 0.035, headCy + s * 0.075).Q(cx, headCy + s * 0.095, cx + s * 0.035, headCy + s * 0.075).pts, { w: 0.9 });
    // toque: band on the forehead, a tall pleated puff above
    const bandB = headCy - hry * 0.58, bandT = bandB - s * 0.065;
    const puff = PB().M(cx - s * 0.135, bandT + s * 0.005)
      .C(cx - s * 0.1462, bandT - s * 0.06, cx - s * 0.2021, bandT - s * 0.1, cx - s * 0.172, bandT - s * 0.17)
      .C(cx - s * 0.172, bandT - s * 0.235, cx - s * 0.12, bandT - s * 0.25, cx - s * 0.08, bandT - s * 0.225)
      .C(cx - s * 0.05, bandT - s * 0.285, cx + s * 0.05, bandT - s * 0.29, cx + s * 0.075, bandT - s * 0.23)
      .C(cx + s * 0.13, bandT - s * 0.255, cx + s * 0.1849, bandT - s * 0.22, cx + s * 0.172, bandT - s * 0.15)
      .C(cx + s * 0.2021, bandT - s * 0.095, cx + s * 0.1462, bandT - s * 0.05, cx + s * 0.135, bandT + s * 0.005).pts;
    kh.fill(puff, P.white);
    kh.shadow(puff, (x, y) => (x - (cx + s * 0.07)) / (s * 0.025) + (y - (bandT - s * 0.2)) / (s * 0.08), { angle: 60 });
    kh.stroke(puff);
    for (const [x0, x1, h] of [[-0.075, -0.1, 0.16], [-0.02, -0.025, 0.2], [0.035, 0.05, 0.19], [0.09, 0.12, 0.14]]) {
      kh.stroke(PB().M(cx + s * x0, bandT - s * 0.005).Q(cx + s * (x0 + x1) / 2 - s * 0.01, bandT - s * h * 0.5, cx + s * x1, bandT - s * h).pts, { w: 0.5, alpha: 0.75 });
    }
    const band = PB().M(cx - s * 0.142, bandB).Q(cx, bandB + s * 0.03, cx + s * 0.142, bandB)
      .L(cx + s * 0.137, bandT).Q(cx, bandT + s * 0.025, cx - s * 0.137, bandT).pts;
    kh.shape(band, P.white);
    kh.shadow(band, (x) => (x - (cx + s * 0.07)) / (s * 0.02), { angle: 70 });
    c.restore();
    c.restore();
  }

  // ---- the pen: sampled paths, a jittery double line, fills off the line, hatching ----

  /** Path builder that samples lines and curves into points. */
  function PB() {
    const pts = []; let x = 0, y = 0;
    const api = {
      M(a, b) { x = a; y = b; pts.push([x, y]); return api; },
      L(a, b) { for (let i = 1; i <= 12; i++) pts.push([x + (a - x) * i / 12, y + (b - y) * i / 12]); x = a; y = b; return api; },
      Q(qx, qy, a, b) { for (let i = 1; i <= 20; i++) { const t = i / 20, u = 1 - t; pts.push([u * u * x + 2 * u * t * qx + t * t * a, u * u * y + 2 * u * t * qy + t * t * b]); } x = a; y = b; return api; },
      C(c1x, c1y, c2x, c2y, a, b) {
        for (let i = 1; i <= 24; i++) {
          const t = i / 24, u = 1 - t;
          pts.push([u * u * u * x + 3 * u * u * t * c1x + 3 * u * t * t * c2x + t * t * t * a, u * u * u * y + 3 * u * u * t * c1y + 3 * u * t * t * c2y + t * t * t * b]);
        }
        x = a; y = b; return api;
      },
      pts,
    };
    return api;
  }

  function penEllipse(cx, cy, rx, ry, rot = 0) {
    const out = [], cr = Math.cos(rot), sr = Math.sin(rot);
    for (let i = 0; i <= 80; i++) { const a = (i / 80) * Math.PI * 2, x = Math.cos(a) * rx, y = Math.sin(a) * ry; out.push([cx + x * cr - y * sr, cy + x * sr + y * cr]); }
    return out;
  }

  /** A sleeve: a closed outline around a curved centre line, tapering from w0 to w1. */
  function penLimb(a, m, b, w0, w1) {
    const L = [], R = [];
    for (let i = 0; i <= 24; i++) {
      const t = i / 24, u = 1 - t;
      const x = u * u * a[0] + 2 * u * t * m[0] + t * t * b[0], y = u * u * a[1] + 2 * u * t * m[1] + t * t * b[1];
      const dx = 2 * u * (m[0] - a[0]) + 2 * t * (b[0] - m[0]), dy = 2 * u * (m[1] - a[1]) + 2 * t * (b[1] - m[1]);
      const d = Math.hypot(dx, dy) || 1, w = (w0 + (w1 - w0) * t) / 2;
      L.push([x - dy / d * w, y + dx / d * w]); R.push([x + dy / d * w, y - dx / d * w]);
    }
    return { outline: L.concat(R.slice().reverse()), end: [L[24], R[0]] };
  }

  const PEN_NOISE = (() => { let x = 7; return Array.from({ length: 256 }, () => { x = (x * 1103515245 + 12345) % 2147483648; return (x / 2147483647) * 2 - 1; }); })();
  function penNoise(seed, x) {
    const i = Math.floor(x), f = x - i, o = seed * 53;
    const a = PEN_NOISE[(i + o) & 255], b = PEN_NOISE[(i + 1 + o) & 255], u = (1 - Math.cos(f * Math.PI)) / 2;
    return a + (b - a) * u;
  }

  /** How the pen draws at figure size s: each line twice, the second a little off and lighter. */
  function penKit(c, s, seed, ink) {
    const LINE = 0.0105, JIT = 0.0032, OFF = [0.006, 0.004];
    const poly = (pts) => { c.beginPath(); c.moveTo(pts[0][0], pts[0][1]); for (let i = 1; i < pts.length; i++) c.lineTo(pts[i][0], pts[i][1]); c.closePath(); };
    function stroke(pts, o = {}) {
      const w = (o.w || 1) * LINE * s, passes = o.passes || 2;
      for (let p = 0; p < passes; p++) {
        const nz = seed++ * 7 + p * 131;
        const shiftX = p ? penNoise(nz, 3.3) * 0.6 * JIT * s : 0, shiftY = p ? penNoise(nz, 7.7) * 0.6 * JIT * s : 0;
        let len = 0;
        c.beginPath();
        for (let i = 0; i < pts.length; i++) {
          if (i) len += Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]);
          const a = pts[Math.max(0, i - 1)], b = pts[Math.min(pts.length - 1, i + 1)];
          const dx = b[0] - a[0], dy = b[1] - a[1], d = Math.hypot(dx, dy) || 1;
          const off = penNoise(nz, len / (s * 0.07)) * JIT * s;
          const x = pts[i][0] - dy / d * off + shiftX, y = pts[i][1] + dx / d * off + shiftY;
          if (i) c.lineTo(x, y); else c.moveTo(x, y);
        }
        c.strokeStyle = ink;
        c.globalAlpha = (o.alpha ?? 1) * (p ? 0.7 : 1);
        c.lineWidth = w * (p ? 0.65 : 1);
        c.lineCap = 'round'; c.lineJoin = 'round';
        c.stroke();
      }
      c.globalAlpha = 1;
    }
    function fill(pts, col, o = {}) {
      c.save();
      if (!o.exact) c.translate(OFF[0] * s, OFF[1] * s);
      poly(pts); c.fillStyle = col; c.fill();
      c.restore();
    }
    function shape(pts, col, o = {}) { fill(pts, col, o); stroke(pts, o); }
    /** Hatching inside pts where side(x, y) > 0. */
    function shadow(pts, side, o = {}) {
      c.save(); poly(pts); c.clip();
      let r = seed++ * 3 + 1;
      const rnd = () => { r = (r * 1103515245 + 12345) % 2147483648; return r / 2147483647; };
      const ang = ((o.angle ?? 62) * Math.PI) / 180, sp = s * (o.spacing ?? 0.014), step = s * 0.004;
      const xs = pts.map((p) => p[0]), ys = pts.map((p) => p[1]);
      const x0 = Math.min(...xs), x1 = Math.max(...xs), y0 = Math.min(...ys), y1 = Math.max(...ys);
      const diag = Math.hypot(x1 - x0, y1 - y0), mx = (x0 + x1) / 2, my = (y0 + y1) / 2;
      const ux = Math.cos(ang), uy = Math.sin(ang), nx = -uy, ny = ux;
      for (let k = -diag; k <= diag; k += sp) {
        let run = null;
        for (let d = -diag; d <= diag + step; d += step) {
          const x = mx + nx * k + ux * d, y = my + ny * k + uy * d;
          const on = d <= diag && side(x, y) > (rnd() - 0.5) * 0.06;
          if (on && !run) run = [[x, y]];
          else if (on) run[1] = [x, y];
          else if (run) {
            if (run[1]) {
              const [a, b] = run;
              stroke([a, [(a[0] + b[0]) / 2 + (rnd() - 0.5) * s * 0.003, (a[1] + b[1]) / 2], b], { w: 0.5, passes: 1, alpha: o.alpha ?? 0.7 });
            }
            run = null;
          }
        }
      }
      c.restore();
    }
    return { stroke, fill, shape, shadow };
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
