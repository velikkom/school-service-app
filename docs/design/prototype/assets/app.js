/* ServisFlow prototip — yönlendirici, prototip araç çubuğu ve olay yönetimi.
 * Adres biçimi: index.html?vp=390&state=empty#/parent/servisim
 *   vp     : 1440 (masaüstü, varsayılan) | 390 (mobil) | 360 (dar mobil)
 *   state  : normal | loading | empty | error | forbidden  (ana içerik alanına uygulanır)
 *   chrome : 0 → prototip araç çubuğunu gizler (ekran görüntüsü için)
 *   notes  : 0 → tasarım notlarını gizler */
(function () {
  const D = window.DB, U = window.UI;
  window.SCREENS = window.SCREENS || { public: {}, admin: {}, driver: {}, hostess: {}, parent: {}, student: {}, shared: {} };
  window.ACTIONS = window.ACTIONS || {};
  const params = new URLSearchParams(location.search);
  const opts = { vp: params.get("vp") || "1440", state: params.get("state") || "normal", chrome: params.get("chrome") !== "0", notes: params.get("notes") !== "0" };
  const DEFAULT = { admin: "operasyon", driver: "bugun", hostess: "bugun", parent: "servisim", student: "servisim", public: "giris" };

  function parseHash() {
    const h = location.hash.replace(/^#\/?/, "");
    const [path, qs] = h.split("?");
    const parts = path.split("/").filter(Boolean);
    const q = Object.fromEntries(new URLSearchParams(qs || ""));
    if (!parts.length || parts[0] === "index") return { role: "index", screen: "index", q };
    return { role: parts[0], screen: parts[1] || DEFAULT[parts[0]], id: parts[2], sub: parts[3], q };
  }

  const APP = window.APP = { ctx: null, opts };
  APP.persona = () => U.PERSONAS[APP.ctx.role];
  APP.go = h => { location.hash = h; };
  APP.render = function () {
    const ctx = APP.ctx = parseHash();
    const root = document.getElementById("app-root");
    document.body.classList.toggle("no-chrome", !opts.chrome);
    document.body.classList.toggle("hide-notes", !opts.notes);
    const stage = document.getElementById("stage");
    stage.className = "stage" + (opts.vp !== "1440" ? " vp-mobile" : "");
    stage.style.setProperty("--vp-w", opts.vp + "px");
    syncBar();
    if (ctx.role === "index") { root.innerHTML = window.renderIndex(); document.title = "ServisFlow · Ekran dizini"; return; }
    let fn = (SCREENS[ctx.role] || {})[ctx.screen] || SCREENS.shared[ctx.screen];
    if (ctx.role === "public") { const page = fn ? fn(ctx) : SCREENS.public.giris(ctx); root.innerHTML = '<div class="app public">' + page.html + "</div>"; document.title = "ServisFlow · " + page.title; return; }
    let page;
    if (!fn) page = { title: "Sayfa bulunamadı", html: U.stateBlock("forbidden") };
    else page = fn(ctx);
    if (opts.state !== "normal" && !page.noState) page = Object.assign({}, page, { html: (page.stateHead || "") + U.stateBlock(opts.state, page.meta) });
    root.innerHTML = U.shell(ctx, page);
    document.title = "ServisFlow · " + page.title;
    if (page.after) page.after();
    if (APP._lastHash !== location.hash) { root.scrollTop = 0; window.scrollTo(0, 0); }
    APP._lastHash = location.hash;
  };

  /* ── Prototip araç çubuğu ── */
  function setParam(k, v) { opts[k] = v; const p = new URLSearchParams(location.search); p.set(k, v); history.replaceState(null, "", "?" + p.toString() + location.hash); APP.render(); }
  function syncBar() {
    const r = document.getElementById("pb-role"), v = document.getElementById("pb-vp"), s = document.getElementById("pb-state");
    if (r) r.value = ["admin", "driver", "hostess", "parent", "student", "public"].includes(APP.ctx.role) ? APP.ctx.role : "";
    if (v) v.value = opts.vp; if (s) s.value = opts.state;
  }
  document.addEventListener("change", e => {
    if (e.target.id === "pb-role" && e.target.value) APP.go("#/" + e.target.value + "/" + DEFAULT[e.target.value]);
    if (e.target.id === "pb-vp") setParam("vp", e.target.value);
    if (e.target.id === "pb-state") setParam("state", e.target.value);
    if (e.target.id === "pb-notes") setParam("notes", e.target.checked ? "1" : "0");
  });

  /* ── Olay delegasyonu ── */
  document.addEventListener("click", e => {
    const a = e.target.closest("[data-act]");
    if (a) {
      const act = a.dataset.act;
      if (act === "dlg-close") { U.closeDialog(); return; }
      if (act === "open-menu") { document.getElementById("m-menu").classList.add("open"); return; }
      if (act === "close-menu") { document.getElementById("m-menu").classList.remove("open"); return; }
      if (act === "reset-data") { D.reset(); return; }
      if (act === "retry") { setParam("state", "normal"); return; }
      const fn = ACTIONS[act];
      if (fn) { e.preventDefault(); try { fn(a, APP.ctx); } catch (ex) { U.toast(ex.message, "err"); } }
      return;
    }
    const row = e.target.closest("tr[data-href]");
    if (row && !e.target.closest("a,button")) APP.go(row.dataset.href);
  });
  window.addEventListener("hashchange", APP.render);
  document.addEventListener("keydown", e => { if (e.key === "Escape") U.closeDialog(); });

  window.addEventListener("DOMContentLoaded", () => {
    const n = document.getElementById("pb-notes"); if (n) n.checked = opts.notes;
    if (!location.hash) location.hash = "#/index";
    APP.render();
  });
})();
