/* ============================================================
   NEBULA Client — Logique du site
   100 % JS vanilla : i18n, canvas, curseur, lightbox, FAQ…
   ============================================================ */
"use strict";

/* ---------- Utilitaires ---------- */
const $  = (sel, ctx = document) => ctx.querySelector(sel);
const $$ = (sel, ctx = document) => Array.from(ctx.querySelectorAll(sel));
const REDUCED = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

const store = {
  get(key) { try { return localStorage.getItem(key); } catch { return null; } },
  set(key, val) { try { localStorage.setItem(key, val); } catch { /* noop */ } }
};

let LANG = store.get("nebula-lang") || "fr";
if (LANG !== "fr" && LANG !== "en") LANG = "fr";
const t = (key) => (I18N[LANG] && I18N[LANG][key]) ?? I18N.fr[key] ?? "";

/* ============================================================
   i18n
   ============================================================ */
function applyI18n() {
  document.documentElement.lang = LANG;

  $$("[data-i18n]").forEach(el => { el.innerHTML = t(el.dataset.i18n); });
  $$("[data-i18n-html]").forEach(el => { el.innerHTML = t(el.dataset.i18nHtml); });
  $$("[data-i18n-aria]").forEach(el => { el.setAttribute("aria-label", t(el.dataset.i18nAria)); });

  $$(".lang-switch button").forEach(btn => {
    const on = btn.dataset.lang === LANG;
    btn.classList.toggle("active", on);
    btn.setAttribute("aria-pressed", String(on));
  });

  renderModules();
  renderGallery();
  renderFaq();
  renderThemeList();
}

function setLang(lang) {
  if (lang === LANG) return;
  LANG = lang;
  store.set("nebula-lang", lang);
  applyI18n();
}

$$(".lang-switch button").forEach(btn => {
  btn.addEventListener("click", () => setLang(btn.dataset.lang));
});

/* ============================================================
   Rendu — Modules
   ============================================================ */
function markVisibleIfInView(scope) {
  $$(".reveal", scope).forEach(el => {
    const r = el.getBoundingClientRect();
    if (r.top < window.innerHeight - 40) el.classList.add("visible");
  });
}

function renderModules() {
  const grid = $("#modules-grid");
  if (!grid) return;
  const revealed = grid.dataset.revealed === "1";

  grid.innerHTML = MODULES.map((m) => `
    <article class="module-card${revealed ? " visible" : " reveal"}" data-cat="${m.cat}">
      <div class="module-icon">${ICONS[m.icon] || ""}</div>
      <h3 class="module-name">${m[LANG].name}</h3>
      <p class="module-desc">${m[LANG].desc}</p>
      <div class="module-tags">
        <span class="module-tag">${CAT_LABELS[LANG][m.cat]}</span>
        ${m.hot ? `<span class="module-tag hot">Signature</span>` : ""}
      </div>
    </article>`).join("");

  observeReveals(grid);
  bindCardGlow(grid);
  updateFilterCounts();
  applyFilter(currentFilter, false);
}

function bindCardGlow(grid) {
  $$(".module-card", grid).forEach(card => {
    card.addEventListener("pointermove", (e) => {
      const r = card.getBoundingClientRect();
      card.style.setProperty("--mx", `${e.clientX - r.left}px`);
      card.style.setProperty("--my", `${e.clientY - r.top}px`);
    });
  });
}

/* ---------- Filtres ---------- */
let currentFilter = "all";

function updateFilterCounts() {
  const counts = { all: MODULES.length };
  MODULES.forEach(m => { counts[m.cat] = (counts[m.cat] || 0) + 1; });
  $$(".filter-count").forEach(el => {
    const c = counts[el.dataset.countCat];
    if (c !== undefined) el.textContent = c;
  });
}

function applyFilter(cat, animate = true) {
  currentFilter = cat;
  const cards = $$("#modules-grid .module-card");
  let shown = 0;
  cards.forEach(card => {
    const show = cat === "all" || card.dataset.cat === cat;
    card.classList.toggle("hidden", !show);
    if (show) {
      if (animate && !REDUCED) {
        card.style.animation = "none";
        void card.offsetWidth; /* reflow pour relancer l'animation */
        card.style.animation = `cardIn .55s cubic-bezier(.22,1,.36,1) ${shown * 40}ms both`;
      }
      shown++;
    }
  });
}

$$(".filter-btn").forEach(btn => {
  btn.addEventListener("click", () => {
    $$(".filter-btn").forEach(b => {
      b.classList.toggle("active", b === btn);
      b.setAttribute("aria-selected", String(b === btn));
    });
    applyFilter(btn.dataset.filter);
  });
});

/* ============================================================
   Rendu — Galerie + Lightbox
   ============================================================ */
function renderGallery() {
  const grid = $("#gallery-grid");
  if (!grid) return;
  const revealed = grid.dataset.revealed === "1";

  grid.innerHTML = GALLERY.map((g, i) => `
    <figure class="g-item${g.featured ? " featured" : ""}${revealed ? " visible" : " reveal"}" data-index="${i}" tabindex="0" role="button"
            aria-label="${g[LANG]}">
      <img src="assets/screenshots/${g.file}" alt="${g[LANG]}" loading="lazy" decoding="async" width="1280" height="720">
      <span class="g-zoom">${ICONS.zoom}</span>
      <figcaption class="g-caption">${g[LANG]}</figcaption>
    </figure>`).join("");

  observeReveals(grid);

  $$(".g-item", grid).forEach(item => {
    item.addEventListener("click", () => openLightbox(Number(item.dataset.index)));
    item.addEventListener("keydown", (e) => {
      if (e.key === "Enter" || e.key === " ") { e.preventDefault(); openLightbox(Number(item.dataset.index)); }
    });
  });
}

/* ---------- Lightbox ---------- */
const lightbox = {
  el: null, img: null, text: null, counter: null, index: 0, open: false,

  init() {
    this.el = $("#lightbox");
    this.img = $("#lightbox-img");
    this.text = $("#lightbox-text");
    this.counter = $("#lightbox-counter");

    $("#lightbox-close").addEventListener("click", () => this.close());
    $("#lightbox-prev").addEventListener("click", () => this.show(this.index - 1));
    $("#lightbox-next").addEventListener("click", () => this.show(this.index + 1));
    $(".lightbox-backdrop").addEventListener("click", () => this.close());

    document.addEventListener("keydown", (e) => {
      if (!this.open) return;
      if (e.key === "Escape") this.close();
      else if (e.key === "ArrowLeft") this.show(this.index - 1);
      else if (e.key === "ArrowRight") this.show(this.index + 1);
    });
  },

  show(i) {
    const n = GALLERY.length;
    this.index = ((i % n) + n) % n;
    const g = GALLERY[this.index];
    this.img.src = `assets/screenshots/${g.file}`;
    this.img.alt = g[LANG];
    this.text.textContent = g[LANG];
    this.counter.textContent = `${this.index + 1} / ${n}`;
    /* préchargement des voisines */
    [1, -1].forEach(d => {
      const img = new Image();
      img.src = `assets/screenshots/${GALLERY[((this.index + d) % n + n) % n].file}`;
    });
  },

  openAt(i) {
    this.show(i);
    this.el.hidden = false;
    requestAnimationFrame(() => requestAnimationFrame(() => this.el.classList.add("open")));
    document.body.style.overflow = "hidden";
    this.open = true;
    $("#lightbox-close").focus();
  },

  close() {
    this.el.classList.remove("open");
    document.body.style.overflow = "";
    this.open = false;
    setTimeout(() => { if (!this.open) this.el.hidden = true; }, 320);
  }
};

function openLightbox(i) { lightbox.openAt(i); }

/* ============================================================
   Rendu — Thèmes
   ============================================================ */
function renderThemeList() {
  const list = $("#theme-list");
  if (!list) return;
  const active = $(".theme-option.active", list);
  const activeId = active ? active.dataset.theme : "nebula";

  list.innerHTML = THEMES.map(th => `
    <button class="theme-option${th.id === activeId ? " active" : ""}" data-theme="${th.id}" role="tab"
            aria-selected="${th.id === activeId}">
      <span class="theme-dots" aria-hidden="true">
        ${th.colors.map(c => `<span style="background:${c}"></span>`).join("")}
      </span>
      <span class="theme-info">
        <span class="theme-name">${th.name}</span>
        <span class="theme-desc">${th[LANG]}</span>
      </span>
      <span class="theme-check" aria-hidden="true">${ICONS.check}</span>
    </button>`).join("");

  $$(".theme-option", list).forEach(btn => {
    btn.addEventListener("click", () => {
      $$(".theme-option", list).forEach(b => {
        const on = b === btn;
        b.classList.toggle("active", on);
        b.setAttribute("aria-selected", String(on));
      });
      applyTheme(btn.dataset.theme);
    });
  });

  applyTheme(activeId, false);
}

function applyTheme(id, animate = true) {
  const th = THEMES.find(x => x.id === id) || THEMES[0];
  const preview = $("#theme-preview");
  if (!preview) return;
  if (!animate) preview.style.setProperty("transition", "none");
  Object.entries(th.vars).forEach(([k, v]) => preview.style.setProperty(k, v));
  preview.dataset.theme = th.id;
  if (!animate) {
    void preview.offsetWidth;
    preview.style.removeProperty("transition");
  }
}

/* ============================================================
   Rendu — FAQ
   ============================================================ */
function renderFaq() {
  const list = $("#faq-list");
  if (!list) return;

  list.innerHTML = FAQ.map((f) => `
    <div class="faq-item">
      <button class="faq-q" aria-expanded="false">
        <span>${f[LANG].q}</span>
        <span class="faq-chevron" aria-hidden="true">${ICONS.chevron}</span>
      </button>
      <div class="faq-a">
        <div class="faq-a-inner"><p>${f[LANG].a}</p></div>
      </div>
    </div>`).join("");

  $$(".faq-item", list).forEach(item => {
    const q = $(".faq-q", item);
    q.addEventListener("click", () => {
      const isOpen = item.classList.contains("open");
      $$(".faq-item.open", list).forEach(other => {
        other.classList.remove("open");
        $(".faq-q", other).setAttribute("aria-expanded", "false");
      });
      if (!isOpen) {
        item.classList.add("open");
        q.setAttribute("aria-expanded", "true");
      }
    });
  });
}

/* ============================================================
   Révélations au scroll + compteurs
   ============================================================ */
const revealObserver = new IntersectionObserver((entries) => {
  entries.forEach(entry => {
    if (!entry.isIntersecting) return;
    const el = entry.target;
    const delay = parseInt(el.dataset.delay || "0", 10);
    el.style.transitionDelay = `${delay}ms`;
    el.classList.add("visible");
    el.closest("[data-reveal-scope]")?.setAttribute("data-revealed", "1");
    setTimeout(() => { el.style.transitionDelay = ""; }, delay + 900);
    revealObserver.unobserve(el);

    if (el.classList.contains("hero-stats")) runCounters(el);
  });
}, { threshold: 0.12, rootMargin: "0px 0px -6% 0px" });

function observeReveals(scope) {
  $$(".reveal", scope || document).forEach(el => revealObserver.observe(el));
}

function runCounters(scope) {
  $$(".counter", scope).forEach(el => {
    if (el.dataset.done === "1") return;
    el.dataset.done = "1";
    const target = parseInt(el.dataset.count, 10);
    if (REDUCED) { el.textContent = target; return; }
    const dur = 1300, start = performance.now();
    const step = (now) => {
      const p = Math.min((now - start) / dur, 1);
      const eased = 1 - Math.pow(1 - p, 3);
      el.textContent = Math.round(target * eased);
      if (p < 1) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  });
}

/* ============================================================
   Navbar
   ============================================================ */
function initNavbar() {
  const nav = $("#navbar");
  const burger = $("#burger");
  const menu = $("#mobile-menu");

  const onScroll = () => nav.classList.toggle("scrolled", window.scrollY > 24);
  window.addEventListener("scroll", onScroll, { passive: true });
  onScroll();

  burger.addEventListener("click", () => {
    const open = menu.classList.toggle("open");
    burger.classList.toggle("open", open);
    burger.setAttribute("aria-expanded", String(open));
  });
  $$("a", menu).forEach(a => a.addEventListener("click", () => {
    menu.classList.remove("open");
    burger.classList.remove("open");
    burger.setAttribute("aria-expanded", "false");
  }));

  /* Lien actif selon la section visible */
  const links = $$("#nav-links a");
  const sections = links
    .map(a => $(a.getAttribute("href")))
    .filter(Boolean);

  const sectionObserver = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (!entry.isIntersecting) return;
      links.forEach(a => a.classList.toggle("active", a.getAttribute("href") === `#${entry.target.id}`));
    });
  }, { rootMargin: "-40% 0px -55% 0px" });
  sections.forEach(s => sectionObserver.observe(s));

  /* Bouton retour en haut */
  const toTop = $("#to-top");
  window.addEventListener("scroll", () => {
    toTop.classList.toggle("show", window.scrollY > 650);
  }, { passive: true });
  toTop.addEventListener("click", () => window.scrollTo({ top: 0, behavior: REDUCED ? "auto" : "smooth" }));
}

/* ============================================================
   Boutons « Copier »
   ============================================================ */
function initCopyButtons() {
  $$("[data-copy]").forEach(btn => {
    btn.addEventListener("click", async () => {
      const text = btn.dataset.copy;
      let ok = false;
      try {
        await navigator.clipboard.writeText(text);
        ok = true;
      } catch {
        const ta = document.createElement("textarea");
        ta.value = text;
        ta.style.position = "fixed";
        ta.style.opacity = "0";
        document.body.appendChild(ta);
        ta.select();
        try { ok = document.execCommand("copy"); } catch { ok = false; }
        ta.remove();
      }
      if (ok) {
        btn.classList.add("copied");
        setTimeout(() => btn.classList.remove("copied"), 1600);
      }
    });
  });
}

/* ============================================================
   Curseur personnalisé
   ============================================================ */
function initCursor() {
  if (!window.matchMedia("(pointer: fine)").matches || REDUCED) return;

  const dot = $("#cursor-dot");
  const ring = $("#cursor-ring");
  const state = { x: -100, y: -100, rx: -100, ry: -100 };

  document.body.classList.add("custom-cursor-on");

  window.addEventListener("pointermove", (e) => {
    state.x = e.clientX;
    state.y = e.clientY;
    dot.style.transform = `translate(${state.x}px, ${state.y}px) translate(-50%, -50%)`;
  }, { passive: true });

  document.addEventListener("pointerover", (e) => {
    const interactive = e.target.closest("a, button, .g-item, .faq-q, .filter-btn, input, [role='button']");
    ring.classList.toggle("hover", Boolean(interactive));
  });

  document.addEventListener("mouseleave", () => document.body.classList.add("cursor-hidden"));
  document.addEventListener("mouseenter", () => document.body.classList.remove("cursor-hidden"));

  /* Anneau à traînée douce, intégré à la boucle principale */
  window.__cursorUpdate = () => {
    state.rx += (state.x - state.rx) * 0.16;
    state.ry += (state.y - state.ry) * 0.16;
    ring.style.transform = `translate(${state.rx}px, ${state.ry}px) translate(-50%, -50%)`;
  };
}

/* ============================================================
   Canvas 1 — Champ d'étoiles (fond fixe)
   ============================================================ */
function initStarfield() {
  const canvas = $("#starfield");
  const ctx = canvas.getContext("2d");
  let W = 0, H = 0, dpr = 1;
  let stars = [];
  let meteors = [];
  let nextMeteor = 2.5;

  const TINTS = [
    "255,255,255", "255,255,255", "255,255,255", "196,181,253",
    "165,243,252", "240,171,252"
  ];

  function resize() {
    dpr = Math.min(window.devicePixelRatio || 1, 2);
    W = window.innerWidth;
    H = window.innerHeight;
    canvas.width = W * dpr;
    canvas.height = H * dpr;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

    const count = Math.min(Math.floor((W * H) / 8500), 240);
    stars = Array.from({ length: count }, () => ({
      x: Math.random() * W,
      y: Math.random() * H,
      r: 0.35 + Math.random() * 1.35,
      a: 0.18 + Math.random() * 0.65,
      tw: 0.4 + Math.random() * 1.6,
      ph: Math.random() * Math.PI * 2,
      depth: 0.2 + Math.random() * 0.8,
      tint: TINTS[Math.floor(Math.random() * TINTS.length)]
    }));
  }

  function spawnMeteor() {
    const fromLeft = Math.random() > 0.5;
    meteors.push({
      x: fromLeft ? Math.random() * W * 0.45 : W * 0.55 + Math.random() * W * 0.45,
      y: Math.random() * H * 0.35,
      vx: (fromLeft ? 1 : -1) * (5.5 + Math.random() * 4),
      vy: 2.2 + Math.random() * 1.8,
      life: 0,
      maxLife: 55 + Math.random() * 35
    });
  }

  function draw(time, scrollY) {
    ctx.clearRect(0, 0, W, H);

    for (const s of stars) {
      const twinkle = 0.55 + 0.45 * Math.sin(time * s.tw + s.ph);
      const alpha = s.a * twinkle;
      let y = s.y - scrollY * s.depth * 0.22;
      y = ((y % H) + H) % H;
      ctx.beginPath();
      ctx.fillStyle = `rgba(${s.tint},${alpha})`;
      ctx.arc(s.x, y, s.r, 0, Math.PI * 2);
      ctx.fill();
    }

    /* Étoiles filantes */
    nextMeteor -= 1 / 60;
    if (nextMeteor <= 0) {
      spawnMeteor();
      nextMeteor = 3.5 + Math.random() * 4.5;
    }
    meteors = meteors.filter(m => m.life < m.maxLife);
    for (const m of meteors) {
      m.x += m.vx;
      m.y += m.vy;
      m.life++;
      const fade = 1 - m.life / m.maxLife;
      const tailX = m.x - m.vx * 9;
      const tailY = m.y - m.vy * 9;
      const grad = ctx.createLinearGradient(m.x, m.y, tailX, tailY);
      grad.addColorStop(0, `rgba(230,225,255,${0.85 * fade})`);
      grad.addColorStop(0.25, `rgba(167,139,250,${0.4 * fade})`);
      grad.addColorStop(1, "rgba(167,139,250,0)");
      ctx.strokeStyle = grad;
      ctx.lineWidth = 1.6;
      ctx.lineCap = "round";
      ctx.beginPath();
      ctx.moveTo(m.x, m.y);
      ctx.lineTo(tailX, tailY);
      ctx.stroke();
      /* tête brillante */
      ctx.beginPath();
      ctx.fillStyle = `rgba(255,255,255,${0.9 * fade})`;
      ctx.arc(m.x, m.y, 1.7, 0, Math.PI * 2);
      ctx.fill();
    }
  }

  window.addEventListener("resize", resize, { passive: true });
  resize();
  return { draw };
}

/* ============================================================
   Canvas 2 — Orbes en orbite (hero)
   ============================================================ */
function initOrbs() {
  const canvas = $("#orbs");
  const ctx = canvas.getContext("2d");
  const hero = $("#hero");
  let W = 0, H = 0, dpr = 1;
  let visible = true;

  const PALETTE = ["#a78bfa", "#22d3ee", "#e879f9", "#818cf8", "#c4b5fd"];

  const ORB_DEFS = [
    { speed: 0.42, rx: 0.30, ry: 0.115, size: 7,  color: "#a78bfa", wob: 1.3 },
    { speed: 0.30, rx: 0.40, ry: 0.155, size: 5,  color: "#22d3ee", wob: 2.6 },
    { speed: 0.55, rx: 0.22, ry: 0.085, size: 4.5, color: "#e879f9", wob: 0.7 },
    { speed: 0.24, rx: 0.46, ry: 0.175, size: 6,  color: "#818cf8", wob: 4.0 },
    { speed: 0.36, rx: 0.345, ry: 0.135, size: 3.6, color: "#c4b5fd", wob: 2.0 }
  ];
  const orbs = ORB_DEFS.map((o, i) => ({
    ...o,
    a: (i / ORB_DEFS.length) * Math.PI * 2,
    trail: []
  }));

  const parallax = { x: 0, y: 0, tx: 0, ty: 0 };

  function resize() {
    dpr = Math.min(window.devicePixelRatio || 1, 2);
    const rect = hero.getBoundingClientRect();
    W = rect.width;
    H = rect.height;
    canvas.width = W * dpr;
    canvas.height = H * dpr;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  }

  window.addEventListener("resize", resize, { passive: true });
  window.addEventListener("pointermove", (e) => {
    parallax.tx = (e.clientX - window.innerWidth / 2) * 0.035;
    parallax.ty = (e.clientY - window.innerHeight / 2) * 0.028;
  }, { passive: true });

  new IntersectionObserver((entries) => {
    visible = entries[0].isIntersecting;
  }, { threshold: 0 }).observe(hero);

  function drawOrb(x, y, size, color, alpha) {
    /* halo */
    const halo = ctx.createRadialGradient(x, y, 0, x, y, size * 4.2);
    halo.addColorStop(0, hexA(color, 0.5 * alpha));
    halo.addColorStop(0.4, hexA(color, 0.16 * alpha));
    halo.addColorStop(1, hexA(color, 0));
    ctx.fillStyle = halo;
    ctx.beginPath();
    ctx.arc(x, y, size * 4.2, 0, Math.PI * 2);
    ctx.fill();
    /* cœur */
    const core = ctx.createRadialGradient(x - size * 0.3, y - size * 0.3, 0, x, y, size);
    core.addColorStop(0, `rgba(255,255,255,${0.95 * alpha})`);
    core.addColorStop(0.5, hexA(color, 0.9 * alpha));
    core.addColorStop(1, hexA(color, 0.25 * alpha));
    ctx.fillStyle = core;
    ctx.beginPath();
    ctx.arc(x, y, size, 0, Math.PI * 2);
    ctx.fill();
  }

  function hexA(hex, a) {
    const n = parseInt(hex.slice(1), 16);
    return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`;
  }

  function draw(time) {
    if (!visible) return;
    ctx.clearRect(0, 0, W, H);

    const cx = W / 2 + parallax.x;
    const cy = H * 0.40 + parallax.y;
    parallax.x += (parallax.tx - parallax.x) * 0.05;
    parallax.y += (parallax.ty - parallax.y) * 0.05;

    /* trajectoires discrètes */
    ctx.strokeStyle = "rgba(167,139,250,0.09)";
    ctx.lineWidth = 1;
    orbs.forEach(o => {
      ctx.beginPath();
      ctx.ellipse(cx, cy, o.rx * W, o.ry * H * (1 + 0.25 * Math.sin(o.wob)), -0.16, 0, Math.PI * 2);
      ctx.stroke();
    });

    /* noyau central pulsant */
    const pulse = 1 + 0.1 * Math.sin(time * 1.8);
    const coreGlow = ctx.createRadialGradient(cx, cy, 0, cx, cy, 150 * pulse);
    coreGlow.addColorStop(0, "rgba(139,92,246,0.22)");
    coreGlow.addColorStop(0.45, "rgba(99,102,241,0.08)");
    coreGlow.addColorStop(1, "rgba(99,102,241,0)");
    ctx.fillStyle = coreGlow;
    ctx.beginPath();
    ctx.arc(cx, cy, 150 * pulse, 0, Math.PI * 2);
    ctx.fill();

    /* positions + profondeur */
    const positions = orbs.map(o => {
      o.a += o.speed / 60;
      const x = cx + Math.cos(o.a) * o.rx * W;
      const helix = Math.sin(o.a * 2 + o.wob) * o.ry * H * 0.45;
      const y = cy + Math.sin(o.a) * o.ry * H + helix;
      const depth = (Math.sin(o.a) + 1) / 2; /* 0 arrière → 1 avant */
      o.trail.push({ x, y, depth });
      if (o.trail.length > 26) o.trail.shift();
      return { o, x, y, depth };
    });

    positions.sort((p1, p2) => p1.depth - p2.depth);

    for (const { o, x, y, depth } of positions) {
      const scale = 0.55 + depth * 0.6;
      const alpha = 0.35 + depth * 0.65;

      /* traînée */
      for (let i = 0; i < o.trail.length; i++) {
        const p = o.trail[i];
        const f = i / o.trail.length;
        ctx.beginPath();
        ctx.fillStyle = hexA(o.color, 0.14 * f * f * alpha);
        ctx.arc(p.x, p.y, o.size * scale * f * 0.85, 0, Math.PI * 2);
        ctx.fill();
      }

      drawOrb(x, y, o.size * scale, o.color, alpha);
    }
  }

  resize();
  return { draw };
}

/* ============================================================
   Boucle d'animation principale
   ============================================================ */
function startLoop(starfield, orbs) {
  if (REDUCED) {
    /* Une seule image statique pour rester léger */
    const paint = () => {
      starfield.draw(0, window.scrollY);
      orbs.draw(0);
      if (window.__cursorUpdate) window.__cursorUpdate();
    };
    paint();
    window.addEventListener("resize", paint, { passive: true });
    window.addEventListener("scroll", () => starfield.draw(0, window.scrollY), { passive: true });
    return;
  }

  let last = performance.now();
  const frame = (now) => {
    const t = now / 1000;
    const dt = Math.min((now - last) / 1000, 0.05);
    last = now;
    starfield.draw(t, window.scrollY);
    orbs.draw(t);
    if (window.__cursorUpdate) window.__cursorUpdate();
    requestAnimationFrame(frame);
  };
  requestAnimationFrame(frame);
}

/* ============================================================
   Initialisation
   ============================================================ */
document.addEventListener("DOMContentLoaded", () => {
  applyI18n();

  lightbox.init();
  initNavbar();
  initCopyButtons();
  initCursor();

  const starfield = initStarfield();
  const orbs = initOrbs();
  startLoop(starfield, orbs);

  /* Révélations des éléments statiques */
  observeReveals(document);

  /* Les conteneurs générés marquent leur état pour les re-rendus i18n */
  ["#modules-grid", "#gallery-grid"].forEach(sel => {
    const el = $(sel);
    if (el) el.setAttribute("data-reveal-scope", "");
  });
});
