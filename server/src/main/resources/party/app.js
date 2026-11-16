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
  };
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
      party = whole(view.party);
      gone = false;
      // the server no longer knows this browser's key: taken off the list, or the name was picked on another phone
      if (ident && view.me !== ident.guestId) forget();
      if (error && error === T.offline) error = null;
    } catch (e) {
      if (e.status === 404) { gone = true; party = null; } else if (!party) error = e.message; else if (e.status === 0) error = T.offline;
    }
    // somebody typing their name keeps the keyboard
    if (!(document.activeElement && document.activeElement.tagName === 'INPUT')) render();
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
      else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
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
        h('div', { class: 'group' }, party.menu.map(p => {
          const n = counts[p.id] || 0;
          return h('div', { class: 'row' + (n > 0 ? ' mine' : '') },
            h('div', { class: 'text' }, h('div', { class: 'name' }, p.name), p.note && h('div', { class: 'sub' }, p.note)),
            h('div', { class: 'stepper' },
              h('button', { type: 'button', 'aria-label': '−1 ' + p.name, disabled: n === 0, onclick: () => change(p.id, -1) }, '−'),
              h('span', { class: 'n' + (n > 0 ? ' on' : ''), 'aria-live': 'polite' }, n),
              h('button', { type: 'button', 'aria-label': '+1 ' + p.name, disabled: total >= MAX_PER_GUEST, onclick: () => change(p.id, 1) }, '+')));
        })),
        h('p', { class: 'status' + (status === T.saved ? ' ok' : ''), role: 'status' }, total === 0 && !draft && !saving ? T.pick : status)),
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
      mine && h('div', { class: 'yours' }, T.yours + ': ' + mine))];
  }

  function doneView() {
    const baked = party.guests.reduce((a, g) => a + g.orders.reduce((b, o) => b + o.baked, 0), 0) + party.spareBaked;
    return [h('div', { class: 'stage' },
      h('div', { class: 'big', 'aria-hidden': 'true' }, '🍕'),
      h('h1', null, T.doneTitle),
      h('p', null, T.done),
      h('div', { class: 'yours' }, party.title + ' · ' + T.baked(baked)))];
  }

  function render() {
    const app = document.getElementById('app');
    let kids;
    if (gone) kids = [h('div', { class: 'stage' }, h('div', { class: 'big', 'aria-hidden': 'true' }, '🫥'), h('h1', null, T.gone), h('p', null, T.goneText))];
    else if (!party) kids = error ? [h('p', { class: 'error', role: 'alert' }, error)] : [h('p', { class: 'loading' }, '🍕')];
    else {
      document.title = party.title;
      if (party.stage === 'BAKING') kids = bakingView();
      else if (party.stage === 'DONE') kids = doneView();
      else kids = me() ? orderView() : joinView();
    }
    app.replaceChildren(...kids.flat().filter(k => k && k.nodeType));
  }

  refresh();
  setInterval(() => { if (!document.hidden && !draft && !saving && !busy) refresh(); }, 8000);
  document.addEventListener('visibilitychange', () => { if (!document.hidden && !draft && !saving) refresh(); });
})();
