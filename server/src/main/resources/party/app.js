// The guests' page of a pizza night. No account: the code in the address invites,
// and this browser remembers which guest it is.
(() => {
  'use strict';

  const code = location.pathname.split('/').filter(Boolean).pop() || '';
  const polish = (navigator.language || 'en').toLowerCase().startsWith('pl');
  const T = polish ? {
    by: 'zaprasza', yourName: 'Twoje imię', join: 'Wchodzę', already: 'Już tu byłeś? Wybierz swoje imię',
    hello: 'Ciao', notYou: 'to nie ja', menu: 'Twoja pizza', bring: 'Kto co przynosi', table: 'Przy stole',
    iBring: 'Przyniosę', you: 'Ty ✓', undo: 'cofnij', choosing: 'jeszcze wybiera', nothing: 'nic nie zamawia',
    saved: 'Zapisane ✓', saving: 'Zapisuję…', pick: 'Wybierz, ile i jakich pizz chcesz.',
    max: 'Najwyżej 6 pizz na osobę.', foot: 'Zamówienia zamyka pizzaiolo, gdy zaczyna piec.',
    bakingTitle: 'Wypiek trwa', baking: 'Odłóż telefon. Pizza idzie.', yours: 'Dla Ciebie',
    doneTitle: 'Grazie!', done: 'To był dobry wieczór.', baked: n => n === 1 ? '1 pizza z pieca' : (n % 10 >= 2 && n % 10 <= 4 && (n % 100 < 12 || n % 100 > 14) ? n + ' pizze z pieca' : n + ' pizz z pieca'),
    gone: 'Ten wypiek nie istnieje', goneText: 'Link wygasł albo jest niepełny. Poproś pizzaiolo o nowy.',
    closed: 'Zamówienia są już zamknięte. Daj znać pizzaiolo.', taken: 'To imię już jest na liście. Jeśli to Ty, wybierz je poniżej; jeśli nie, dopisz literę.',
    offline: 'Brak połączenia. Spróbuję ponownie.', oops: 'Coś poszło nie tak. Spróbuj jeszcze raz.', pizzaiolo: 'pizzaiolo',
    count: n => n === 0 ? 'Jeszcze nic nie wybrano' : n === 1 ? 'Zamawiasz 1 pizzę' : n < 5 ? 'Zamawiasz ' + n + ' pizze' : 'Zamawiasz ' + n + ' pizz',
    tonight: 'Dzisiejsze menu', search: 'Szukaj pizzy lub składnika', byIngredient: 'Składniki',
    found: (n, all) => 'Pasuje: ' + n + ' z ' + all, noMatch: q => 'Żadna pizza nie pasuje do „' + q + '”.', showAll: 'Pokaż wszystkie',
    prev: 'Poprzednia pizza', next: 'Następna pizza', goTo: 'Pokaż',
  } : {
    by: 'hosted by', yourName: 'Your name', join: 'Count me in', already: 'Been here before? Tap your name',
    hello: 'Ciao', notYou: 'not me', menu: 'Your pizza', bring: 'Who brings what', table: 'At the table',
    iBring: "I'll bring it", you: 'You ✓', undo: 'undo', choosing: 'still choosing', nothing: 'no pizza',
    saved: 'Saved ✓', saving: 'Saving…', pick: 'Pick which pizzas you want, and how many.',
    max: 'At most 6 pizzas per person.', foot: 'Orders close when the pizzaiolo starts the bake.',
    bakingTitle: 'The bake is on', baking: 'Put your phone away. Pizza is coming.', yours: 'For you',
    doneTitle: 'Grazie!', done: 'That was a good night.', baked: n => n === 1 ? '1 pizza out of the oven' : n + ' pizzas out of the oven',
    gone: 'This pizza night does not exist', goneText: 'The link expired or is incomplete. Ask the pizzaiolo for a new one.',
    closed: 'Orders are closed. Ask the pizzaiolo.', taken: 'That name is already on the list. If it is you, tap it below; if not, add a letter.',
    offline: 'No connection. Trying again.', oops: 'Something went wrong. Try again.', pizzaiolo: 'pizzaiolo',
    count: n => n === 0 ? 'Nothing picked yet' : n === 1 ? 'You ordered 1 pizza' : 'You ordered ' + n + ' pizzas',
    tonight: "Tonight's menu", search: 'Search a pizza or an ingredient', byIngredient: 'Ingredients',
    found: (n, all) => n + ' of ' + all + ' pizzas', noMatch: q => 'No pizza matches “' + q + '”.', showAll: 'Show all',
    prev: 'Previous pizza', next: 'Next pizza', goTo: 'Show',
  };
  const LANG = polish ? 'pl' : 'en';
  const P = window.VPPizza;
  const still = () => window.matchMedia && matchMedia('(prefers-reduced-motion: reduce)').matches;
  const MAX_PER_GUEST = 6;
  document.documentElement.lang = polish ? 'pl' : 'en';

  // ---- what this browser remembers: which guest it is ----
  const KEY = 'vp-party-' + code;
  const memory = {
    get() { try { return JSON.parse(localStorage.getItem(KEY)); } catch (e) { return null; } },
    set(v) { try { if (v) localStorage.setItem(KEY, JSON.stringify(v)); else localStorage.removeItem(KEY); } catch (e) { /* private window */ } },
  };

  let ident = memory.get();   // { guestId, key }
  let party = null;
  let gone = false;
  let error = null;
  let joinName = '';
  let busy = false;
  // my order while I am tapping: pizza id -> count; null when the server's answer is the truth
  let draft = null;
  let saveTimer = 0;
  let saving = false;
  let savedAt = 0;
  // the menu: what the guest searched, which pizzas already had their toppings fall, the last counts drawn
  let query = '';
  const seen = new Set();
  let drawnCounts = null;
  let scrolledAt = 0;
  let lastView = '';

  async function api(method, path, body) {
    const headers = { Accept: 'application/json' };
    if (ident && ident.key) headers.Authorization = 'Bearer ' + ident.key;
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    let r;
    try {
      r = await fetch('/parties/' + encodeURIComponent(code) + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), cache: 'no-store' });
    } catch (e) {
      const err = new Error(T.offline); err.status = 0; throw err;
    }
    let data = null;
    try { data = await r.json(); } catch (e) { /* no body */ }
    if (!r.ok) {
      const said = r.status === 403 && polish ? T.closed : (data && data.error) || T.oops;
      const err = new Error(said); err.status = r.status; throw err;
    }
    return data;
  }

  // the server leaves out what is empty or zero: fill it in once, here
  function whole(p) {
    lastView = '';
    p.stage = p.stage || 'ORDERS';
    p.whenText = p.whenText || ''; p.note = p.note || '';
    p.spareBaked = p.spareBaked || 0;
    p.menu = (p.menu || []).map(m => ({ id: m.id, name: m.name, note: m.note || '' }));
    p.bring = (p.bring || []).map(b => ({ id: b.id, name: b.name, guestId: b.guestId || null }));
    p.guests = (p.guests || []).map(g => ({
      id: g.id, name: g.name, host: !!g.host,
      orders: (g.orders || []).map(o => ({ pizzaId: o.pizzaId, count: o.count || 0, baked: o.baked || 0 })),
    }));
    return p;
  }

  function remember(joined) {
    ident = { guestId: joined.guestId, key: joined.key };
    memory.set(ident);
  }
  function forget() { ident = null; draft = null; memory.set(null); }

  async function refresh() {
    try {
      const view = await api('GET', '');
      const sig = JSON.stringify(view);
      if (sig === lastView && party && !gone && !error) return;
      party = whole(view.party);
      lastView = sig;
      gone = false;
      // the server no longer knows this browser's key: taken off the list, or the name was picked on another phone
      if (ident && view.me !== ident.guestId) forget();
      if (error && error === T.offline) error = null;
    } catch (e) {
      if (e.status === 404) { gone = true; party = null; } else if (!party) error = e.message; else if (e.status === 0) error = T.offline;
    }
    redraw();
  }

  // somebody typing keeps the keyboard, somebody swiping the menu keeps the pizza under their finger
  function redraw() {
    if (document.activeElement && document.activeElement.tagName === 'INPUT') return;
    if (Date.now() - scrolledAt < 700) { setTimeout(redraw, 400); return; }
    render();
  }

  const me = () => (party && ident ? party.guests.find(g => g.id === ident.guestId) : null) || null;
  const pizzaName = id => { const m = party.menu.find(p => p.id === id); return m ? m.name : ''; };
  const ordersText = g => g.orders.filter(o => o.count > 0).map(o => pizzaName(o.pizzaId) + (o.count > 1 ? ' ×' + o.count : '')).join(', ');

  function myCounts() {
    if (draft) return draft;
    const g = me();
    const counts = {};
    if (g) for (const o of g.orders) counts[o.pizzaId] = o.count;
    return counts;
  }

  // ---- actions ----
  async function join() {
    const name = joinName.trim();
    if (!name || busy) return;
    busy = true; error = null; render();
    try {
      const joined = await api('POST', '/join', { name });
      remember(joined); party = whole(joined.party); joinName = '';
    } catch (e) {
      error = e.status === 409 ? T.taken : e.message;
    }
    busy = false; render();
  }

  async function claim(guestId) {
    if (busy) return;
    busy = true; error = null; render();
    try {
      const joined = await api('POST', '/claim', { guestId });
      remember(joined); party = whole(joined.party);
    } catch (e) { error = e.message; }
    busy = false; render();
  }

  function change(pizzaId, by) {
    const counts = Object.assign({}, myCounts());
    const total = Object.values(counts).reduce((a, b) => a + b, 0);
    const next = (counts[pizzaId] || 0) + by;
    if (next < 0 || (by > 0 && total >= MAX_PER_GUEST)) return;
    counts[pizzaId] = next;
    draft = counts; error = null;
    clearTimeout(saveTimer);
    saveTimer = setTimeout(save, 500);
    render();
  }

  async function save() {
    if (saving) { saveTimer = setTimeout(save, 200); return; }
    if (!draft || !ident) return;
    const sent = draft;
    saving = true; render();
    try {
      const orders = Object.entries(sent).filter(([, n]) => n > 0).map(([pizzaId, count]) => ({ pizzaId, count }));
      const updated = await api('PUT', '/guests/' + ident.guestId + '/order', { orders });
      if (draft === sent) { draft = null; party = whole(updated); savedAt = Date.now(); }
    } catch (e) {
      error = e.message;
      if (draft === sent) draft = null;
      saving = false;
      await refresh();
      return;
    }
    saving = false; render();
  }

  async function bring(item, mine) {
    if (busy) return;
    busy = true; error = null; render();
    try { party = whole(await api('PUT', '/bring/' + item.id, { guestId: mine ? null : ident.guestId })); } catch (e) { error = e.message; await refresh(); }
    busy = false; render();
  }

  // ---- drawing: every name on this page is somebody's text, so it only ever goes in as text ----
  function h(tag, props, ...kids) {
    const el = document.createElement(tag);
    for (const [k, v] of Object.entries(props || {})) {
      if (k === 'class') el.className = v;
      else if (k.startsWith('on')) el[k] = v;
      else if (k === 'value') el.value = v;
      else if (v === true) el.setAttribute(k, '');
      else if (v !== false && v != null) el.setAttribute(k, v);
    }
    for (const kid of kids.flat()) {
      if (kid == null || kid === false) continue;
      el.append(kid.nodeType ? kid : document.createTextNode(String(kid)));
    }
    return el;
  }

  function header() {
    const host = party.guests.find(g => g.host);
    const when = [party.whenText, host ? T.by + ' ' + host.name : ''].filter(Boolean).join(' · ');
    return h('header', null,
      h('div', { class: 'brand' }, '🍕 VibePizzaiolo'),
      h('h1', null, party.title),
      when && h('p', { class: 'when' }, when),
      party.note && h('p', { class: 'note' }, party.note));
  }

  function joinView() {
    const others = party.guests.filter(g => !g.host);
    return [
      header(),
      h('form', { class: 'join', onsubmit: e => { e.preventDefault(); join(); } },
        h('input', {
          class: 'field', type: 'text', maxlength: '30', placeholder: T.yourName, 'aria-label': T.yourName, autocomplete: 'given-name',
          enterkeyhint: 'go', value: joinName, oninput: e => { joinName = e.target.value; },
        }),
        h('button', { class: 'btn big', type: 'submit', disabled: busy }, T.join)),
      error && h('p', { class: 'error', role: 'alert' }, error),
      others.length > 0 && h('section', null,
        h('h2', null, T.already),
        h('div', { class: 'chips' }, others.map(g => h('button', { class: 'chip', type: 'button', onclick: () => claim(g.id) }, g.name)))),
      party.menu.length > 0 && h('section', null, h('h2', null, T.tonight), menuBlock(null, 0)),
      tableSection(),
    ];
  }

  function tableSection() {
    if (party.guests.length === 0) return null;
    return h('section', null,
      h('h2', null, T.table + ' · ' + party.guests.length),
      h('div', { class: 'group' }, party.guests.map(g => h('div', { class: 'row' + (ident && g.id === ident.guestId ? ' mine' : '') },
        h('div', { class: 'text' },
          h('div', { class: 'name' }, g.name + (g.host ? ' · ' + T.pizzaiolo : '')),
          h('div', { class: 'sub' }, ordersText(g) || T.choosing))))));
  }

  function orderView() {
    const g = me();
    const counts = myCounts();
    const total = Object.values(counts).reduce((a, b) => a + b, 0);
    const status = saving || draft ? T.saving : Date.now() - savedAt < 4000 ? T.saved : total >= MAX_PER_GUEST ? T.max : T.count(total);
    return [
      header(),
      h('div', { class: 'hello' },
        h('strong', null, T.hello + ', ' + g.name + '!'),
        h('button', { class: 'link', type: 'button', onclick: () => { forget(); render(); } }, T.notYou)),
      error && h('p', { class: 'error', role: 'alert' }, error),
      h('section', null,
        h('h2', null, T.menu),
        menuBlock(counts, total),
        h('p', { class: 'status' + (status === T.saved ? ' ok' : ''), role: 'status' }, total === 0 && !draft && !saving ? T.pick : status),
        picked(counts)),
      party.bring.length > 0 && h('section', null,
        h('h2', null, T.bring),
        h('div', { class: 'group' }, party.bring.map(item => {
          const mine = item.guestId === g.id;
          const taker = item.guestId && party.guests.find(x => x.id === item.guestId);
          return h('div', { class: 'row' + (mine ? ' mine' : '') },
            h('div', { class: 'text' }, h('div', { class: 'name' }, item.name)),
            mine ? [h('span', { class: 'who me' }, T.you), h('button', { class: 'link', type: 'button', onclick: () => bring(item, true) }, T.undo)]
              : taker ? h('span', { class: 'who' }, taker.name)
                : h('button', { class: 'btn', type: 'button', disabled: busy, onclick: () => bring(item, false) }, T.iBring));
        }))),
      tableSection(),
      h('p', { class: 'foot' }, T.foot),
    ];
  }

  function bakingView() {
    const g = me();
    const mine = g ? ordersText(g) : '';
    return [h('div', { class: 'stage' },
      h('div', { class: 'big flame', 'aria-hidden': 'true' }, '🔥'),
      h('h1', null, T.bakingTitle),
      h('p', null, T.baking),
      mine && h('div', { class: 'yours' }, T.yours + ': ' + mine),
      g && minis(g))];
  }

  function minis(g) {
    const mine = g.orders.filter(o => o.count > 0);
    if (mine.length === 0) return null;
    return h('div', { class: 'minis', 'aria-hidden': 'true' }, mine.map(o => {
      const p = party.menu.find(m => m.id === o.pizzaId);
      if (!p) return null;
      return h('div', { class: 'mini' }, h('div', { class: 'spin' }, P.picture(P.analyze(p, LANG), P.seedOf(p.id + p.name))), o.count > 1 ? '×' + o.count : '');
    }));
  }

  function doneView() {
    const baked = party.guests.reduce((a, g) => a + g.orders.reduce((b, o) => b + o.baked, 0), 0) + party.spareBaked;
    return [h('div', { class: 'stage' },
      h('div', { class: 'big', 'aria-hidden': 'true' }, '🍕'),
      h('h1', null, T.doneTitle),
      h('p', null, T.done),
      h('div', { class: 'yours' }, party.title + ' · ' + T.baked(baked)))];
  }

  // ---- the menu: a row of pizzas to swipe through, photographed or drawn from their ingredients ----
  const matches = (search, words) => words.every(w => search.includes(w));
  const words = () => P.fold(query).trim().split(/\s+/).filter(Boolean);

  function card(p, counts, total) {
    const info = P.analyze(p, LANG);
    const n = counts ? counts[p.id] || 0 : 0;
    const art = P.picture(info, P.seedOf(p.id + p.name));
    // the first time a pizza comes into view its toppings fall, or its photo lands; never again on a redraw
    if (!seen.has(p.id) && !still() && 'IntersectionObserver' in window) art.classList.add('waiting');
    const spin = h('div', { class: 'spin' + (info.photo ? ' real' : '') }, art);
    const same = info.photo || [info.base, ...[...info.draw].sort()].join(',');
    // a redraw carries on the turn where it was: the turntable's period, from the clock
    spin.style.animationDelay = '-' + ((Date.now() / 1000) % (info.photo ? 48 : 120)).toFixed(2) + 's';
    const popped = counts && drawnCounts && (drawnCounts[p.id] || 0) !== n;
    const q = words();
    return h('article', { class: 'card' + (n > 0 ? ' mine' : ''), 'data-id': p.id, 'data-search': info.search, 'aria-label': p.name, hidden: !matches(info.search, q) },
      h('div', { class: 'art' },
        h('div', { class: 'roll', 'data-keep': p.id + '|' + same }, spin),
        info.photo && h('div', { class: 'shine', 'aria-hidden': 'true' }),
        h('div', { class: 'steam', 'aria-hidden': 'true' }, h('i'), h('i'), h('i')),
        n > 0 && h('span', { class: 'badge' + (popped ? ' pop' : '') }, '×' + n)),
      h('h3', null, p.name),
      info.chips.length > 0 && h('ul', { class: 'ings', 'aria-label': T.byIngredient },
        info.chips.map(c => h('li', { class: 'ing' + (c.neg ? ' neg' : ''), 'data-s': P.fold(c.text) }, P.icon(c.icon), h('span', null, c.text)))),
      counts && h('div', { class: 'stepper' },
        h('button', { type: 'button', 'data-key': 'minus:' + p.id, 'aria-label': '−1 ' + p.name, disabled: n === 0, onclick: () => change(p.id, -1) }, '−'),
        h('span', { class: 'n' + (n > 0 ? ' on' : ''), 'aria-live': 'polite' }, n),
        h('button', { type: 'button', 'data-key': 'plus:' + p.id, 'aria-label': '+1 ' + p.name, disabled: total >= MAX_PER_GUEST, onclick: () => change(p.id, 1) }, '+')));
  }

  /** The menu with its search; [counts] null shows it without steppers, before joining. */
  function menuBlock(counts, total) {
    const many = party.menu.length > 1;
    const tags = new Map();
    for (const p of party.menu) for (const t of P.analyze(p, LANG).tags) if (!tags.has(t.k)) tags.set(t.k, t);
    const block = h('div', { class: 'menu' },
      many && h('label', { class: 'search' }, P.glyph('search'),
        h('input', {
          class: 'field', type: 'search', placeholder: T.search, 'aria-label': T.search, value: query,
          autocomplete: 'off', enterkeyhint: 'search', spellcheck: 'false',
          oninput: e => { query = e.target.value; filter(); },
          onkeydown: e => { if (e.key === 'Enter') e.target.blur(); else if (e.key === 'Escape') { query = ''; e.target.value = ''; filter(); } },
        })),
      many && tags.size > 1 && h('div', { class: 'tags', role: 'group', 'aria-label': T.byIngredient },
        [...tags.values()].map(t => h('button', {
          class: 'tag', type: 'button', 'data-label': t.label, 'aria-pressed': 'false',
          onclick: () => { query = P.fold(query).trim() === P.fold(t.label) ? '' : t.label; const i = document.querySelector('.search input'); if (i) i.value = query; filter(); },
        }, P.icon(t.icon), t.label))),
      many && h('p', { class: 'found', 'aria-live': 'polite' }),
      h('div', { class: 'carousel-wrap' },
        h('div', { class: 'carousel' + (many ? '' : ' single') }, party.menu.map(p => card(p, counts, total))),
        many && h('button', { class: 'arrow prev', type: 'button', 'aria-label': T.prev, onclick: () => step(-1) }, P.glyph('prev')),
        many && h('button', { class: 'arrow next', type: 'button', 'aria-label': T.next, onclick: () => step(1) }, P.glyph('next'))),
      many && h('div', { class: 'dots', 'data-keep': 'dots' }),
      many && h('div', { class: 'no-match', hidden: true },
        h('p', null), h('button', { class: 'link', type: 'button', onclick: () => { query = ''; const i = document.querySelector('.search input'); if (i) i.value = ''; filter(); } }, T.showAll)));
    if (counts) drawnCounts = Object.assign({}, counts);
    return block;
  }

  /** What this guest ordered, as chips that bring the pizza into view. */
  function picked(counts) {
    const mine = party.menu.filter(p => counts[p.id] > 0);
    if (mine.length === 0) return null;
    return h('div', { class: 'picked' }, mine.map(p => h('button', {
      class: 'chip', type: 'button', 'aria-label': T.goTo + ' ' + p.name,
      onclick: () => { const c = cardOf(p.id); if (c && c.hidden) { query = ''; const i = document.querySelector('.search input'); if (i) i.value = ''; filter(); } goTo(cardOf(p.id)); },
    }, p.name + (counts[p.id] > 1 ? ' ×' + counts[p.id] : ''))));
  }

  const carousel = () => document.querySelector('.carousel');
  const shownCards = () => { const el = carousel(); return el ? [...el.children].filter(c => !c.hidden) : []; };
  const cardOf = id => (carousel() ? [...carousel().children] : []).find(c => c.dataset.id === id) || null;

  function goTo(c) {
    const el = carousel();
    if (!el || !c) return;
    el.scrollTo({ left: c.offsetLeft + c.offsetWidth / 2 - el.clientWidth / 2, behavior: still() ? 'auto' : 'smooth' });
  }
  function step(by) {
    const cards = shownCards();
    const now = cards.findIndex(c => c.classList.contains('active'));
    goTo(cards[Math.max(0, Math.min(cards.length - 1, (now < 0 ? 0 : now) + by))]);
  }

  /** Shows the pizzas that match the search, without redrawing: the keyboard stays up. */
  function filter() {
    const el = carousel();
    if (!el) return;
    const q = words();
    for (const c of el.children) c.hidden = !matches(c.dataset.search, q);
    sync();
    el.scrollLeft = 0;
    if (el.vpUpdate) el.vpUpdate();
  }

  /** The count line, the empty state, the matching ingredients and the dots, as the search stands. */
  function sync() {
    const el = carousel();
    if (!el) return;
    const q = words();
    const shown = shownCards().length;
    for (const li of el.querySelectorAll('.ing')) li.classList.toggle('hit', q.length > 0 && q.some(w => li.dataset.s.includes(w)));
    for (const b of document.querySelectorAll('.tag')) b.setAttribute('aria-pressed', String(q.length > 0 && P.fold(b.dataset.label) === q.join(' ')));
    const found = document.querySelector('.found');
    if (found) found.textContent = q.length ? T.found(shown, el.children.length) : '';
    const none = document.querySelector('.no-match');
    if (none) { none.hidden = shown > 0; none.firstChild.textContent = T.noMatch(query.trim()); }
    el.classList.toggle('single', shown <= 1);
    dots();
  }

  function dots() {
    const box = document.querySelector('.dots');
    if (!box) return;
    const cards = shownCards();
    // the dots stay where they are, so the one for the pizza in the middle does not shrink and grow again
    const want = cards.length > 1 ? cards : [];
    const was = [...box.children];
    want.forEach((c, i) => {
      const dot = was[i] || box.appendChild(h('button', { class: 'dot', type: 'button' }));
      dot.setAttribute('aria-label', c.getAttribute('aria-label'));
      dot.onclick = () => goTo(c);
    });
    was.slice(want.length).forEach(d => d.remove());
  }

  /** Cards grow toward the middle and roll as they move; toppings fall when a pizza first shows. */
  function wire() {
    const el = carousel();
    if (!el) return;
    let frame = 0;
    const update = () => {
      const mid = el.scrollLeft + el.clientWidth / 2;
      const cards = shownCards();
      const dotEls = document.querySelectorAll('.dot');
      let best = -1, bestD = Infinity;
      cards.forEach((c, i) => {
        const along = (c.offsetLeft + c.offsetWidth / 2 - mid) / (c.offsetWidth || 1);
        const d = Math.min(Math.abs(along), 1);
        if (Math.abs(along) < bestD) { bestD = Math.abs(along); best = i; }
        c.style.transform = cards.length > 1 ? 'scale(' + (1 - d * .1).toFixed(3) + ')' : '';
        c.style.opacity = cards.length > 1 ? (1 - d * .4).toFixed(3) : '';
        const roll = c.querySelector('.roll');
        if (roll) roll.style.transform = still() ? '' : 'rotate(' + (along * 110).toFixed(1) + 'deg)';
      });
      cards.forEach((c, i) => c.classList.toggle('active', i === best));
      dotEls.forEach((d, i) => d.classList.toggle('on', i === best));
      const prev = document.querySelector('.arrow.prev'), next = document.querySelector('.arrow.next');
      if (prev) prev.disabled = best <= 0;
      if (next) next.disabled = best < 0 || best >= cards.length - 1;
    };
    el.vpUpdate = update;
    if (!el.vpWired) {
      el.vpWired = true;
      el.addEventListener('scroll', () => {
        scrolledAt = Date.now();
        if (!frame) frame = requestAnimationFrame(() => { frame = 0; el.vpUpdate(); });
      }, { passive: true });
    }
    if (el.vpSeen) el.vpSeen.disconnect();
    if ('IntersectionObserver' in window) {
      const io = el.vpSeen = new IntersectionObserver(entries => {
        for (const e of entries) {
          if (!e.isIntersecting) continue;
          const svg = e.target.querySelector('.pizza.waiting');
          if (svg) { svg.classList.remove('waiting'); svg.classList.add('fresh'); }
          seen.add(e.target.dataset.id);
          io.unobserve(e.target);
        }
      }, { root: el, threshold: .5 });
      for (const c of el.children) if (c.querySelector('.pizza.waiting')) io.observe(c);
    }
    sync();
    update();
  }

  // ---- a redraw changes the page in place: what did not change does not move ----
  const HANDLERS = ['onclick', 'oninput', 'onkeydown', 'onsubmit'];

  /** Turns [old] into [nu]; an element for another pizza, or of another kind, is swapped whole. */
  function morph(old, nu) {
    if (old.nodeType !== nu.nodeType || old.nodeName !== nu.nodeName ||
      (old.nodeType === 1 && (old.getAttribute('data-id') || '') !== (nu.getAttribute('data-id') || ''))) {
      old.replaceWith(nu);
      return;
    }
    if (old.nodeType !== 1) { if (old.nodeValue !== nu.nodeValue) old.nodeValue = nu.nodeValue; return; }
    // the same pizza, still turning and rolling: left exactly as it is
    const keep = old.getAttribute('data-keep');
    if (keep && keep === nu.getAttribute('data-keep')) return;
    for (const a of [...old.attributes]) {
      // the carousel sets a card's size and roll as it scrolls: a redraw has no say in it
      if (!nu.hasAttribute(a.name) && a.name !== 'style') old.removeAttribute(a.name);
    }
    for (const a of nu.attributes) {
      let v = a.value;
      // which card is in the middle is the carousel's to say, too
      if (a.name === 'class' && old.classList.contains('active') && old.classList.contains('card')) v += ' active';
      if (old.getAttribute(a.name) !== v) old.setAttribute(a.name, v);
    }
    for (const k of HANDLERS) if (old[k] !== nu[k]) old[k] = nu[k];
    if ('value' in nu && nu.tagName === 'INPUT' && old !== document.activeElement && old.value !== nu.value) old.value = nu.value;
    morphKids(old, nu);
  }

  function morphKids(old, nu) {
    const was = [...old.childNodes];
    const now = [...nu.childNodes];
    now.forEach((kid, i) => { if (i < was.length) morph(was[i], kid); else old.append(kid); });
    for (let i = now.length; i < was.length; i++) was[i].remove();
  }

  function render() {
    const app = document.getElementById('app');
    // a redraw keeps the menu where it was scrolled to, and the button that had focus
    const before = carousel();
    const left = before ? before.scrollLeft : null;
    const focused = document.activeElement && document.activeElement.dataset ? document.activeElement.dataset.key : null;
    let kids;
    if (gone) kids = [h('div', { class: 'stage' }, h('div', { class: 'big', 'aria-hidden': 'true' }, '🫥'), h('h1', null, T.gone), h('p', null, T.goneText))];
    else if (!party) kids = error ? [h('p', { class: 'error', role: 'alert' }, error)] : [h('p', { class: 'loading' }, '🍕')];
    else {
      document.title = party.title;
      if (party.stage === 'BAKING') kids = bakingView();
      else if (party.stage === 'DONE') kids = doneView();
      else kids = me() ? orderView() : joinView();
    }
    const next = document.createElement('main');
    next.append(...kids.flat().filter(k => k && k.nodeType));
    morphKids(app, next);
    const after = carousel();
    if (after && left != null) after.scrollLeft = left;
    wire();
    if (focused) {
      const el = app.querySelector('[data-key="' + focused + '"]');
      if (el && !el.disabled) el.focus({ preventScroll: true });
    }
  }

  refresh();
  setInterval(() => { if (!document.hidden && !draft && !saving && !busy) refresh(); }, 8000);
  document.addEventListener('visibilitychange', () => { if (!document.hidden && !draft && !saving) refresh(); });
})();
