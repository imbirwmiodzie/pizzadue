// Pizzas for the guests' page. The pizzaiolo only types a name and a note ("tomato, mozzarella,
// basil"), so the ingredient icons and the picture of each pizza are read from those words:
// English, Polish and Italian. Everything is drawn here, as SVG: the page loads nothing else.
(() => {
  'use strict';

  const fold = s => String(s || '').toLowerCase().replace(/ł/g, 'l').normalize('NFD').replace(/[̀-ͯ]/g, '');

  // ---- ingredients: the first entry that matches a piece of the note gives its icon ----
  // [key, icon, word starts, English, Polish]; a match takes its words out, so "olive oil" is not olives too
  const ENTRIES = [
    ['four', '🧀', ['four chees', 'quattro formagg', '4 formagg', '4 chees', 'cztery ser', '4 ser'], 'four cheeses', 'cztery sery'],
    ['oil', '🫗', ['olive oil', 'oliwa z oliwek', 'extra virgin', 'olio', 'oliwa', 'oliwy'], 'olive oil', 'oliwa'],
    ['chili', '🌶️', ['spicy', 'hot\\b', 'chil', 'peperoncin', 'piccant', 'ostr', 'pikant', 'jalapen', 'habaner', 'sriracha'], 'chili', 'chili'],
    ['cherry', '🍅', ['cherry', 'pomodorin', 'pomidork', 'datterin', 'pachino', 'koktajl'], 'cherry tomatoes', 'pomidorki'],
    ['tomato', '🍅', ['tomat', 'pomodor', 'pomidor', 'passat', 'pelat', 'san marzano', 'sugo'], 'tomato', 'pomidory'],
    ['blue', '🧀', ['gorgonzol', 'blue chees', 'dolcelatte', 'roquefort', 'plesn', 'lazur'], 'gorgonzola', 'gorgonzola'],
    ['parmesan', '🧀', ['parmigian', 'parmesan', 'parmezan', 'grana', 'pecorin'], 'parmesan', 'parmezan'],
    ['mozzarella', '🧀', ['mozzarel', 'mozarel', 'fior di latte', 'fiordilatte', 'bufal', 'burrat', 'stracciatell'], 'mozzarella', 'mozzarella'],
    ['cheese', '🧀', ['chees', 'formagg', 'ser(?:a|em|y|ami|ow)?\\b', 'ricott', 'provol', 'scamorz', 'fontin', 'taleggi', 'cheddar', 'feta', 'kozi', 'goat', 'emmental', 'gouda', 'camembert', 'brie\\b'], 'cheese', 'ser'],
    ['salami', 'svg:salami', ['salam', 'pepperon', 'spianat', 'chorizo', 'nduja', 'soppressat', 'kabanos'], 'salami', 'salami'],
    ['sausage', '🌭', ['sausag', 'salsicc', 'kielbas', 'wurst', 'merguez'], 'sausage', 'kiełbasa'],
    ['bacon', '🥓', ['bacon', 'pancett', 'boczek', 'boczk', 'guancial', 'lardo'], 'bacon', 'boczek'],
    ['ham', 'svg:ham', ['ham\\b', 'prosciutt', 'szynk', 'cotto', 'crudo', 'speck', 'jambon', 'schinken', 'serrano', 'parma\\b', 'bresaol', 'mortadell'], 'ham', 'szynka'],
    ['chicken', '🍗', ['chicken', 'pollo', 'kurczak', 'kurczecia', 'kurczaki', 'indyk', 'turkey', 'tacchin'], 'chicken', 'kurczak'],
    ['shrimp', '🦐', ['shrimp', 'prawn', 'gamber', 'krewet', 'scamp', 'seafood', 'frutti di mare', 'owoce morza', 'owocami morza', 'cozz', 'mussel', 'malz'], 'seafood', 'owoce morza'],
    ['tuna', '🐟', ['tuna', 'tonno', 'tunczyk', 'salmon', 'salmone', 'losos'], 'tuna', 'tuńczyk'],
    ['anchovy', '🐟', ['anchov', 'acciug', 'alici', 'anchois', 'sardel', 'sardin'], 'anchovies', 'anchois'],
    ['truffle', '🍄', ['truffl', 'tartuf', 'trufl'], 'truffle', 'trufla'],
    ['mushroom', '🍄', ['mushroom', 'fungh', 'pieczark', 'grzyb', 'champignon', 'porcin', 'borowik', 'shiitake'], 'mushrooms', 'pieczarki'],
    ['olive', '🫒', ['oliv', 'oliwk', 'kalamat'], 'olives', 'oliwki'],
    ['artichoke', 'svg:artichoke', ['artichok', 'carciof', 'karczoch'], 'artichokes', 'karczochy'],
    ['veg', '🥗', ['vegetab', 'verdur', 'warzyw', 'ortolan', 'veggie', 'vegetarian', 'wegetarian'], 'vegetables', 'warzywa'],
    ['aubergine', '🍆', ['aubergin', 'eggplant', 'melanzan', 'baklazan'], 'aubergine', 'bakłażan'],
    ['courgette', '🥒', ['courgett', 'zucchin', 'cukini'], 'courgette', 'cukinia'],
    ['pepper', '🫑', ['bell pepper', 'pepper(?!on)', 'peperon(?!cin)', 'papryk', 'paprik', 'friggitell'], 'peppers', 'papryka'],
    ['onion', '🧅', ['onion', 'cipoll', 'cebul', 'tropea', 'scalogn', 'shallot', 'szalotk', 'leek'], 'onion', 'cebula'],
    ['garlic', '🧄', ['garlic', 'aglio', 'czosn'], 'garlic', 'czosnek'],
    ['rocket', '🥬', ['rocket', 'arugul', 'rucol', 'rukol'], 'rocket', 'rukola'],
    ['spinach', '🥬', ['spinach', 'spinac', 'szpinak', 'kale\\b', 'jarmuz'], 'spinach', 'szpinak'],
    ['broccoli', '🥦', ['broccol', 'brokul', 'friariell', 'cime di rapa'], 'broccoli', 'brokuły'],
    ['basil', '🌿', ['basil', 'bazyli', 'pesto'], 'basil', 'bazylia'],
    ['herbs', '🌱', ['oregan', 'origan', 'herb', 'ziol', 'rosemar', 'rosmarin', 'rozmaryn', 'thym', 'tymian', 'timo\\b', 'majeran', 'marjoram'], 'herbs', 'zioła'],
    ['capers', '🫛', ['caper', 'capper', 'kapar'], 'capers', 'kapary'],
    ['corn', '🌽', ['corn', 'mais\\b', 'kukurydz'], 'corn', 'kukurydza'],
    ['pineapple', '🍍', ['pineappl', 'ananas'], 'pineapple', 'ananas'],
    ['egg', '🥚', ['egg', 'uov', 'jajk', 'jaj', 'jajo'], 'egg', 'jajko'],
    ['potato', '🥔', ['potato', 'patat', 'ziemniak', 'kartofl'], 'potato', 'ziemniaki'],
    ['lemon', '🍋', ['lemon', 'limon', 'cytryn'], 'lemon', 'cytryna'],
    ['honey', '🍯', ['honey', 'miele', 'miod'], 'honey', 'miód'],
    ['chocolate', '🍫', ['nutell', 'chocolat', 'cioccolat', 'czekolad', 'cocoa', 'kakao'], 'chocolate', 'czekolada'],
    ['nuts', '🥜', ['nuts?\\b', 'walnut', 'hazelnut', 'noci\\b', 'nocciol', 'pistach', 'pistacch', 'orzech', 'almond', 'mandorl', 'migdal', 'pinoli', 'pine nut'], 'nuts', 'orzechy'],
    ['banana', '🍌', ['banan'], 'banana', 'banan'],
    ['strawberry', '🍓', ['strawberr', 'fragol', 'truskaw'], 'strawberries', 'truskawki'],
    ['pear', '🍐', ['pears?\\b', 'pera\\b', 'pere\\b', 'grusz'], 'pear', 'gruszka'],
    ['cream', '🥛', ['cream', 'panna', 'smietan', 'mascarpon', 'besciamell', 'bechamel', 'beszamel'], 'cream', 'śmietana'],
    ['dessert', '🍰', ['dessert', 'deser', 'dolce', 'sweet', 'slodk'], 'dessert', 'deser'],
    ['white', '⚪', ['bianca', 'white', 'biala', 'bialy', 'bez sosu'], 'white base', 'biały spód'],
  ].map(([k, icon, starts, en, pl]) => ({ k, icon, en, pl, re: new RegExp('(?:^|[^a-z0-9])(?:' + starts.join('|') + ')[a-z]*') }));
  const BY_KEY = Object.fromEntries(ENTRIES.map(e => [e.k, e]));
  const CHEESES = ['four', 'blue', 'parmesan', 'mozzarella', 'cheese'];
  const UNKNOWN = '🍽️';

  // classics whose name says what is on them, for a menu without notes
  const RECIPES = [
    [/margherit|bufal/, ['tomato', 'mozzarella', 'basil']],
    [/marinar/, ['tomato', 'garlic', 'herbs'], { noCheese: true }],
    [/diavol|inferno/, ['tomato', 'mozzarella', 'salami', 'chili']],
    [/capricc|quattro stagion|4 stagion|cztery por/, ['tomato', 'mozzarella', 'ham', 'mushroom', 'artichoke', 'olive']],
    [/napoli|napoletan|romana/, ['tomato', 'mozzarella', 'anchovy', 'capers', 'herbs']],
    [/hawa/, ['tomato', 'mozzarella', 'ham', 'pineapple']],
    [/quattro formagg|4 formagg|cztery ser|four chees/, ['four'], { white: true }],
    [/bianca/, ['mozzarella', 'herbs', 'oil'], { white: true }],
    [/ortolan|vegetarian|wegetarian|primavera/, ['tomato', 'mozzarella', 'veg']],
    [/pepperoni/, ['tomato', 'mozzarella', 'salami']],
    [/tonno/, ['tomato', 'mozzarella', 'tuna', 'onion']],
    [/nutell/, ['chocolate']],
  ];

  /** The entries found in one piece of text, in order, each one's words taken out once found. */
  function scan(text) {
    let rest = ' ' + fold(text) + ' ';
    const found = [];
    for (const e of ENTRIES) {
      const m = e.re.exec(rest);
      if (!m) continue;
      found.push(e);
      rest = rest.slice(0, m.index) + ' '.repeat(m[0].length) + rest.slice(m.index + m[0].length);
    }
    return found;
  }

  const NOT = /^\s*(?:no|bez|senza|without|niente|nie)\s+/;
  const SPLIT = /[,;\n•·\/+&()]|\s(?:and|i|e|oraz|with|con|z|ze|plus)\s/i;

  const cache = new Map();

  /**
   * Everything the page needs about one menu pizza: [chips] to show under the picture,
   * what to [draw], the [tags] it can be found by, and the folded text to [search].
   */
  function analyze(pizza, lang) {
    const id = lang + '|' + pizza.name + '|' + (pizza.note || '');
    if (cache.has(id)) return cache.get(id);
    const label = e => lang === 'pl' ? e.pl : e.en;
    const kinds = new Set();
    const negated = new Set();
    const tags = new Map();
    const chips = [];

    for (const raw of String(pizza.note || '').split(SPLIT)) {
      const piece = raw.trim();
      if (!piece || chips.length >= 10) continue;
      const neg = NOT.test(fold(piece));
      const found = scan(fold(piece).replace(NOT, ''));
      chips.push({ icon: found.length ? found[0].icon : UNKNOWN, text: piece, neg });
      for (const e of found) {
        (neg ? negated : kinds).add(e.k);
        if (!neg) tags.set(e.k, e);
      }
    }

    const name = fold(pizza.name);
    const recipe = RECIPES.find(([re]) => re.test(name));
    const fromName = scan(pizza.name);
    fromName.forEach(e => { if (!negated.has(e.k)) { kinds.add(e.k); tags.set(e.k, e); } });
    const flags = (recipe && recipe[2]) || {};
    if (recipe) for (const k of recipe[1]) if (!negated.has(k)) kinds.add(k);
    // no note: the classic recipe, or what the name itself says
    if (chips.length === 0) {
      const keys = recipe ? recipe[1] : fromName.map(e => e.k);
      for (const k of keys) { const e = BY_KEY[k]; chips.push({ icon: e.icon, text: label(e), neg: false }); tags.set(k, e); }
    }

    const dessert = kinds.has('chocolate') || kinds.has('dessert') || (!kinds.has('tomato') && ['strawberry', 'banana', 'pear'].some(k => kinds.has(k)) && !CHEESES.some(k => kinds.has(k)));
    const white = !dessert && (flags.white || kinds.has('white') || kinds.has('cream') || negated.has('tomato') ||
      (kinds.has('four') && !kinds.has('tomato')));
    const noCheese = dessert || flags.noCheese || CHEESES.some(k => negated.has(k));
    const draw = new Set([...kinds].filter(k => !negated.has(k)));
    if (draw.has('veg')) ['aubergine', 'courgette', 'pepper'].forEach(k => draw.add(k));
    if (draw.has('four')) ['mozzarella', 'cheese', 'blue', 'parmesan'].forEach(k => draw.add(k));
    if (!noCheese && !CHEESES.some(k => draw.has(k))) draw.add('mozzarella');

    tags.delete('white');
    const words = [pizza.name, pizza.note || ''].concat([...tags.values()].flatMap(e => [e.en, e.pl]));
    const info = {
      chips, draw, base: dessert ? 'chocolate' : white ? 'white' : 'tomato',
      tags: [...tags.values()].map(e => ({ k: e.k, icon: e.icon, label: label(e) })),
      search: fold(words.join(' ')),
    };
    cache.set(id, info);
    return info;
  }

  // ---- drawing ----
  const NS = 'http://www.w3.org/2000/svg';
  function s(tag, attrs, ...kids) {
    const el = document.createElementNS(NS, tag);
    for (const [k, v] of Object.entries(attrs || {})) el.setAttribute(k, v);
    for (const kid of kids.flat()) if (kid) el.append(kid);
    return el;
  }
  const TAU = Math.PI * 2;
  const r1 = v => Math.round(v * 10) / 10;

  function seedOf(text) {
    let h = 2166136261;
    for (let i = 0; i < text.length; i++) { h ^= text.charCodeAt(i); h = Math.imul(h, 16777619); }
    return h >>> 0;
  }
  function random(seed) {
    let a = seed >>> 0;
    return () => {
      a = (a + 0x6D2B79F5) >>> 0;
      let t = a;
      t = Math.imul(t ^ (t >>> 15), t | 1);
      t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  /** A soft, uneven round shape: dough, sauce, a blob of mozzarella. */
  function blob(rnd, cx, cy, r, wobble, n) {
    const pts = [];
    for (let i = 0; i < n; i++) {
      const a = i / n * TAU;
      const rr = r * (1 + (rnd() * 2 - 1) * wobble);
      pts.push([cx + Math.cos(a) * rr, cy + Math.sin(a) * rr]);
    }
    const mid = (p, q) => [(p[0] + q[0]) / 2, (p[1] + q[1]) / 2];
    let m = mid(pts[n - 1], pts[0]);
    let d = 'M' + r1(m[0]) + ' ' + r1(m[1]);
    for (let i = 0; i < n; i++) {
      const p = pts[i];
      m = mid(p, pts[(i + 1) % n]);
      d += 'Q' + r1(p[0]) + ' ' + r1(p[1]) + ' ' + r1(m[0]) + ' ' + r1(m[1]);
    }
    return d + 'Z';
  }

  const BASE_COLOUR = { tomato: '#C93A25', white: '#F3E7C9', chocolate: '#4A2A1A' };

  // each topping: how many, how far apart, which layer (cheese under, leaves on top), and its shape
  const TOPPINGS = {
    mozzarella: { n: 7, gap: 13, layer: 1, shape: rnd => [s('path', { d: blob(rnd, 0, 0, 12 + rnd() * 3, .18, 8), fill: '#FBF5E6', stroke: '#E8D7B0', 'stroke-width': '.8' })] },
    cheese: { n: 6, gap: 12, layer: 1, shape: rnd => [s('path', { d: blob(rnd, 0, 0, 10 + rnd() * 3, .2, 8), fill: '#F4CE68', stroke: '#DDAA40', 'stroke-width': '.8' })] },
    blue: {
      n: 4, gap: 10, layer: 1, shape: rnd => [s('path', { d: blob(rnd, 0, 0, 8, .25, 7), fill: '#E6E8E0', stroke: '#C9CFC6', 'stroke-width': '.6' }),
        ...[0, 1, 2].map(() => s('circle', { cx: r1(rnd() * 8 - 4), cy: r1(rnd() * 8 - 4), r: '1.3', fill: '#5F7E8C' }))],
    },
    parmesan: { n: 5, gap: 8, layer: 3, shape: () => [s('path', { d: 'M-7 -3L6 -5L8 2L-5 4Z', fill: '#F3E2B0', stroke: '#D5BD82', 'stroke-width': '.6' })] },
    cream: { n: 5, gap: 12, layer: 1, shape: rnd => [s('path', { d: blob(rnd, 0, 0, 11, .3, 7), fill: '#FFFBF0', opacity: '.9' })] },
    salami: {
      n: 9, gap: 12, layer: 2, shape: rnd => [s('circle', { r: '11', fill: '#A3271F', stroke: '#7A1A14', 'stroke-width': '1.4' }),
        ...[0, 1, 2, 3, 4].map(() => s('circle', { cx: r1(rnd() * 13 - 6.5), cy: r1(rnd() * 13 - 6.5), r: r1(1 + rnd() * .7), fill: '#E8A094' }))],
    },
    ham: { n: 5, gap: 13, layer: 2, shape: rnd => [s('path', { d: blob(rnd, 0, 0, 12, .28, 7), fill: '#EA9890', stroke: '#F7CDC6', 'stroke-width': '2.2' })] },
    bacon: {
      n: 5, gap: 12, layer: 2, shape: () => [s('path', { d: 'M-12 -3Q-6 -7 0 -3T12 -3L12 3Q6 -1 0 3T-12 3Z', fill: '#BE4E43' }),
        s('path', { d: 'M-11 0Q-6 -4 0 0T11 0', stroke: '#F3D3C4', 'stroke-width': '1.6', fill: 'none' })],
    },
    sausage: { n: 9, gap: 8, layer: 2, shape: rnd => [0, 1, 2].map(i => s('path', { d: blob(rnd, i * 3.5 - 3.5, (i % 2) * 3 - 1.5, 3.4 + rnd() * 1.4, .3, 6), fill: '#8A5134', stroke: '#653822', 'stroke-width': '.6' })) },
    chicken: {
      n: 7, gap: 10, layer: 2, shape: rnd => [s('path', { d: blob(rnd, 0, 0, 6.5, .3, 6), fill: '#E2BE8A', stroke: '#C2955C', 'stroke-width': '.8' }),
        s('path', { d: 'M-4 -2L3 -4M-3 2L4 0', stroke: '#9C6B3A', 'stroke-width': '1', 'stroke-linecap': 'round' })],
    },
    shrimp: {
      n: 5, gap: 11, layer: 2, shape: () => [s('path', { d: 'M-7 3A7 7 0 1 1 5 5', stroke: '#F0855A', 'stroke-width': '4.6', 'stroke-linecap': 'round', fill: 'none' }),
        s('path', { d: 'M-5 -3L-2 -1M0 -6L0 -3M5 -4L3 -2', stroke: '#FFC3A6', 'stroke-width': '.9', 'stroke-linecap': 'round' })],
    },
    tuna: { n: 7, gap: 10, layer: 2, shape: rnd => [s('path', { d: blob(rnd, 0, 0, 6, .35, 6), fill: '#D8B194', stroke: '#B48A6C', 'stroke-width': '.6' }), s('path', { d: 'M-3 -1L3 1', stroke: '#B48A6C', 'stroke-width': '.7' })] },
    anchovy: { n: 6, gap: 10, layer: 2, shape: () => [s('path', { d: 'M-12 0Q0 -3.4 12 0Q0 3.4 -12 0Z', fill: '#7D5D4B' }), s('path', { d: 'M-10 0Q0 -1.5 10 0', stroke: '#C8BDB2', 'stroke-width': '.7', fill: 'none' })] },
    truffle: { n: 7, gap: 9, layer: 3, shape: rnd => [s('path', { d: blob(rnd, 0, 0, 5, .3, 6), fill: '#3F302A', stroke: '#8C7464', 'stroke-width': '.7', 'stroke-dasharray': '2 1.5' })] },
    mushroom: {
      n: 7, gap: 10, layer: 2, shape: () => [s('path', { d: 'M-9 0C-9 -9 9 -9 9 0L3 0L3 7Q0 8.4 -3 7L-3 0Z', fill: '#D3BCA0', stroke: '#8E7056', 'stroke-width': '.9' }),
        s('path', { d: 'M-6 -1L-4 -5M0 -1L0 -6M6 -1L4 -5', stroke: '#A98A6E', 'stroke-width': '.6' })],
    },
    olive: { n: 8, gap: 7, layer: 2, shape: (rnd, base) => [s('circle', { r: '4.6', fill: '#231C22' }), s('circle', { r: '1.8', fill: BASE_COLOUR[base] })] },
    artichoke: {
      n: 4, gap: 13, layer: 2, shape: () => [-35, 0, 35].map(a => s('path', {
        d: 'M0 4C-6 0 -5 -9 0 -12C5 -9 6 0 0 4Z', fill: a ? '#8E9C50' : '#A9B66A', stroke: '#66743A', 'stroke-width': '.8', transform: 'rotate(' + a + ')',
      })),
    },
    aubergine: {
      n: 4, gap: 13, layer: 2, shape: () => [s('circle', { r: '9', fill: '#EEDDB2', stroke: '#4E2450', 'stroke-width': '2.4' }),
        s('path', { d: 'M-6 -3L6 -3M-6 3L6 3', stroke: '#7A5530', 'stroke-width': '1.3', opacity: '.7' })],
    },
    courgette: {
      n: 5, gap: 11, layer: 2, shape: () => [s('circle', { r: '7.5', fill: '#E7EDBE', stroke: '#3F7A33', 'stroke-width': '2' }),
        ...[0, 1, 2, 3].map(i => s('circle', { cx: r1(Math.cos(i * 1.57) * 2.5), cy: r1(Math.sin(i * 1.57) * 2.5), r: '.8', fill: '#B9C78A' }))],
    },
    pepper: { n: 6, gap: 9, layer: 2, shape: rnd => [s('path', { d: 'M-9 3Q0 -6 9 3', stroke: ['#D63A2A', '#F0BE2C', '#3F8E3A'][Math.floor(rnd() * 3)], 'stroke-width': '4', 'stroke-linecap': 'round', fill: 'none' })] },
    onion: { n: 6, gap: 7, layer: 2, shape: () => [s('path', { d: 'M-8 2A8 8 0 0 1 8 2M-5 2A5 5 0 0 1 5 2', stroke: '#A65A93', 'stroke-width': '1.8', fill: 'none', 'stroke-linecap': 'round' })] },
    garlic: { n: 9, gap: 5, layer: 2, shape: () => [s('ellipse', { rx: '3.2', ry: '2.2', fill: '#F6EFDC', stroke: '#D7C79E', 'stroke-width': '.6' })] },
    cherry: {
      n: 6, gap: 10, layer: 2, shape: () => [s('circle', { r: '6.5', fill: '#E1352B' }), s('circle', { r: '4.4', fill: '#F06A50' }),
        ...[0, 1, 2].map(i => s('circle', { cx: r1(Math.cos(i * 2.1) * 2.4), cy: r1(Math.sin(i * 2.1) * 2.4), r: '.8', fill: '#F7D9A3' }))],
    },
    chili: {
      n: 7, gap: 5, layer: 3, shape: () => [s('circle', { r: '2.9', fill: '#F2C08F', stroke: '#C8261A', 'stroke-width': '1.5' }), s('circle', { cx: '.6', r: '.6', fill: '#FFF1D6' })],
    },
    spinach: { n: 6, gap: 10, layer: 2, shape: rnd => [s('path', { d: blob(rnd, 0, 0, 7, .3, 7), fill: '#2E6A39', opacity: '.92' })] },
    broccoli: {
      n: 6, gap: 9, layer: 2, shape: () => [s('path', { d: 'M-1.3 2L1.3 2L1 7L-1 7Z', fill: '#8DB35E' }),
        ...[[-3, -1], [3, -1], [0, -4], [0, 0]].map(([x, y], i) => s('circle', { cx: x, cy: y, r: '3', fill: i % 2 ? '#2E5F2C' : '#3D7D3A' }))],
    },
    capers: { n: 10, gap: 4, layer: 3, shape: () => [s('circle', { r: '2.2', fill: '#6E8B3D', stroke: '#4F6A2B', 'stroke-width': '.5' })] },
    corn: { n: 16, gap: 3, layer: 3, shape: () => [s('circle', { r: '2.4', fill: '#F2C230', stroke: '#D49A16', 'stroke-width': '.5' })] },
    pineapple: { n: 6, gap: 9, layer: 2, shape: () => [s('path', { d: 'M-4 0L8 -5Q11 0 8 5Z', fill: '#F6CF4E', stroke: '#DFA92A', 'stroke-width': '.7' })] },
    egg: { n: 1, gap: 18, layer: 2, shape: rnd => [s('path', { d: blob(rnd, 0, 0, 13, .18, 8), fill: '#FFFDF5' }), s('circle', { cx: '1', cy: '-1', r: '5', fill: '#F4AE22' })] },
    potato: { n: 6, gap: 10, layer: 2, shape: () => [s('circle', { r: '8', fill: '#EED9A0', stroke: '#CFA65A', 'stroke-width': '1' }), s('circle', { cx: '-2', cy: '-2', r: '2.4', fill: '#F8EBC4' })] },
    lemon: {
      n: 4, gap: 11, layer: 2, shape: () => [s('circle', { r: '7', fill: '#FBE68A', stroke: '#F2C21F', 'stroke-width': '1.6' }),
        s('path', { d: 'M0 -6L0 6M-5.2 -3L5.2 3M-5.2 3L5.2 -3', stroke: '#F6D65C', 'stroke-width': '.7' })],
    },
    nuts: { n: 10, gap: 4, layer: 3, shape: rnd => [s('path', { d: blob(rnd, 0, 0, 3, .35, 6), fill: '#A66F3F', stroke: '#7A4E2A', 'stroke-width': '.5' })] },
    banana: { n: 6, gap: 10, layer: 2, shape: () => [s('circle', { r: '6', fill: '#F6E7A8', stroke: '#E3CB72', 'stroke-width': '1' }), s('circle', { r: '1.2', fill: '#C9AE5E' })] },
    strawberry: {
      n: 6, gap: 10, layer: 2, shape: () => [s('path', { d: 'M0 7C-7 2 -7 -5 0 -5C7 -5 7 2 0 7Z', fill: '#E2323F' }),
        ...[[-2, -2], [2, -1], [0, 2]].map(([x, y]) => s('circle', { cx: x, cy: y, r: '.6', fill: '#FBE3A0' }))],
    },
    pear: { n: 5, gap: 10, layer: 2, shape: () => [s('path', { d: 'M0 -8C3 -8 3 -3 5 1C7 6 3 8 0 8C-3 8 -7 6 -5 1C-3 -3 -3 -8 0 -8Z', fill: '#EFE6B3', stroke: '#B9B05A', 'stroke-width': '.8' })] },
    rocket: {
      n: 7, gap: 6, layer: 4, shape: () => [s('path', { d: 'M-11 0L-8 -2L-6 -1L-4 -4L-1 -2L2 -5L4 -2L7 -3L11 0L7 3L4 2L2 5L-1 2L-4 4L-6 1L-8 2Z', fill: '#4F8F35', stroke: '#3B6E27', 'stroke-width': '.5' })],
    },
    basil: {
      n: 5, gap: 9, layer: 4, shape: () => [s('path', { d: 'M-10 0C-5 -7 5 -7 10 0C5 7 -5 7 -10 0Z', fill: '#2E8B47' }),
        s('path', { d: 'M-9 0L9 0', stroke: '#63BF74', 'stroke-width': '.7' })],
    },
  };
  // drawn as specks or a drizzle, not one by one
  const SPECKS = { herbs: ['#4B7A3C', 46, 1.1], chili: ['#C9301E', 18, .9] };
  const DRIZZLE = { honey: ['#E9A41E', .85, 2], oil: ['#D9C24A', .5, 2.6], chocolate: ['#2E160B', .9, 2.2] };

  let uid = 0;

  /**
   * One pizza as an SVG, its toppings in groups of class "drop" (each with its own --dl delay)
   * so the page can let them fall onto the dough. The same [seed] always lays them out the same.
   */
  function paint(info, seed) {
    const rnd = random(seed);
    const id = 'vp' + (++uid);
    const base = info.base;
    const svg = s('svg', { viewBox: '-100 -100 200 200', class: 'pizza', 'aria-hidden': 'true', focusable: 'false' });
    const sauce = {
      tomato: [['0', '#DA4B2F'], ['1', '#B02C1D']],
      white: [['0', '#FCF3DE'], ['1', '#E9D9B2']],
      chocolate: [['0', '#6A3E25'], ['1', '#3B2013']],
    }[base];
    svg.append(s('defs', null,
      s('radialGradient', { id: id + 'c', cx: '50%', cy: '45%', r: '55%' },
        s('stop', { offset: '0', 'stop-color': '#F3CD8A' }), s('stop', { offset: '.72', 'stop-color': '#E3A85E' }), s('stop', { offset: '1', 'stop-color': '#B6733A' })),
      s('radialGradient', { id: id + 's', cx: '45%', cy: '40%', r: '65%' }, sauce.map(([o, c]) => s('stop', { offset: o, 'stop-color': c })))));

    // cornicione with leopard spots, then the sauce
    svg.append(s('path', { d: blob(rnd, 0, 0, 94, .028, 30), fill: 'url(#' + id + 'c)' }));
    for (let i = 0; i < 20; i++) {
      const a = rnd() * TAU, d = 86 + rnd() * 7;
      svg.append(s('circle', { cx: r1(Math.cos(a) * d), cy: r1(Math.sin(a) * d), r: r1(1.4 + rnd() * 2.8), fill: '#5B3317', opacity: r1(.45 + rnd() * .4) }));
    }
    svg.append(s('path', { d: blob(rnd, 0, 0, 80, .045, 16), fill: 'url(#' + id + 's)', stroke: 'rgba(120,60,20,.25)', 'stroke-width': '2.5' }));
    if (base === 'tomato') {
      for (let i = 0; i < 9; i++) {
        const a = rnd() * TAU, d = Math.sqrt(rnd()) * 62;
        svg.append(s('path', { d: blob(rnd, Math.cos(a) * d, Math.sin(a) * d, 6 + rnd() * 7, .3, 7), fill: '#E35E3E', opacity: '.45' }));
      }
    }

    const kinds = [...info.draw].filter(k => TOPPINGS[k] || SPECKS[k] || DRIZZLE[k]);
    const many = kinds.filter(k => TOPPINGS[k]).length;
    const scale = Math.max(.5, Math.min(1, 1.45 - many * .13));
    const placed = [];
    const spot = (reach, gap) => {
      let x = 0, y = 0;
      for (let t = 0; t < 40; t++) {
        const a = rnd() * TAU, d = Math.sqrt(rnd()) * reach;
        x = Math.cos(a) * d; y = Math.sin(a) * d;
        if (placed.every(p => Math.hypot(p[0] - x, p[1] - y) > (gap + p[2]) * .85)) break;
      }
      placed.push([x, y, gap]);
      return [x, y];
    };

    const layers = [[], [], [], [], [], []];
    let order = 0;
    const put = (layer, x, y, rot, kids) => {
      const drop = s('g', { class: 'drop' }, kids);
      drop.style.setProperty('--dl', (layer * 110 + Math.min(order++, 32) * 22) + 'ms');
      layers[layer].push(s('g', { transform: 'translate(' + r1(x) + ' ' + r1(y) + ') rotate(' + Math.round(rot) + ')' }, drop));
    };

    for (const k of kinds) {
      const t = TOPPINGS[k];
      if (t && !(k === 'chili' && kinds.includes('salami'))) {
        const n = k === 'egg' ? 1 : Math.max(2, Math.round(t.n * scale));
        for (let i = 0; i < n; i++) {
          const [x, y] = k === 'egg' ? [rnd() * 10 - 5, rnd() * 10 - 5] : spot(66, t.gap);
          put(t.layer, x, y, rnd() * 360, t.shape(rnd, base));
        }
      }
      if (SPECKS[k]) {
        const [colour, n, r] = SPECKS[k];
        const kids = [];
        for (let i = 0; i < n * (k === 'chili' && TOPPINGS[k] && !kinds.includes('salami') ? .5 : 1); i++) {
          const a = rnd() * TAU, d = Math.sqrt(rnd()) * 70;
          kids.push(s('rect', { x: r1(Math.cos(a) * d), y: r1(Math.sin(a) * d), width: r1(r * (1 + rnd() * 1.6)), height: r1(r * .8), fill: colour, transform: 'rotate(' + Math.round(rnd() * 180) + ' ' + r1(Math.cos(a) * d) + ' ' + r1(Math.sin(a) * d) + ')' }));
        }
        put(4, 0, 0, 0, kids);
      }
      if (DRIZZLE[k]) {
        const [colour, opacity, width] = DRIZZLE[k];
        let d = 'M-58 ' + r1(rnd() * 20 - 40);
        for (let x = -40; x <= 60; x += 18) d += 'Q' + r1(x - 9) + ' ' + r1(rnd() * 100 - 50) + ' ' + x + ' ' + r1(rnd() * 70 - 35);
        put(5, 0, 0, rnd() * 360, [s('path', { d, stroke: colour, 'stroke-width': width, opacity, fill: 'none', 'stroke-linecap': 'round' })]);
      }
    }
    layers.forEach(l => l.forEach(g => svg.append(g)));
    return svg;
  }

  // ---- icons: emoji, or a drawing where no emoji says it ----
  const DRAWN = {
    salami: () => [s('circle', { cx: '12', cy: '12', r: '10', fill: '#B32C23', stroke: '#7A1A14', 'stroke-width': '1.5' }),
      ...[[8, 8], [15, 9], [10, 15], [16, 15], [12, 12]].map(([x, y]) => s('circle', { cx: x, cy: y, r: '1.5', fill: '#F0B0A6' }))],
    ham: () => [s('path', { d: 'M4 13C2 7 8 2 14 3C20 4 23 10 20 16C17 21 8 21 4 13Z', fill: '#EA9890', stroke: '#FBE1DC', 'stroke-width': '2' }),
      s('path', { d: 'M8 12C9 9 13 7 16 9', stroke: '#F6C3BC', 'stroke-width': '1.4', fill: 'none', 'stroke-linecap': 'round' })],
    artichoke: () => [s('path', { d: 'M12 22L12 18', stroke: '#6E7D3B', 'stroke-width': '2', 'stroke-linecap': 'round' }),
      s('path', { d: 'M12 19C5 17 4 9 7 5C9 9 10 12 12 13C14 12 15 9 17 5C20 9 19 17 12 19Z', fill: '#8E9C50' }),
      s('path', { d: 'M12 14C9 11 9 6 12 2C15 6 15 11 12 14Z', fill: '#B3BF72' })],
  };

  function icon(code) {
    const span = document.createElement('span');
    span.className = 'ic';
    span.setAttribute('aria-hidden', 'true');
    if (String(code).startsWith('svg:')) span.append(s('svg', { viewBox: '0 0 24 24', focusable: 'false' }, DRAWN[code.slice(4)]()));
    else span.textContent = code;
    return span;
  }

  const GLYPHS = {
    search: ['M10.5 4a6.5 6.5 0 1 1 0 13a6.5 6.5 0 0 1 0 -13zM15.5 15.5L20 20'],
    prev: ['M14.5 5L8 12l6.5 7'],
    next: ['M9.5 5L16 12l-6.5 7'],
  };
  function glyph(name) {
    return s('svg', { viewBox: '0 0 24 24', class: 'glyph', 'aria-hidden': 'true', focusable: 'false' },
      GLYPHS[name].map(d => s('path', { d, fill: 'none', stroke: 'currentColor', 'stroke-width': '2.2', 'stroke-linecap': 'round', 'stroke-linejoin': 'round' })));
  }

  window.VPPizza = { analyze, paint, icon, glyph, fold, seedOf };
})();
