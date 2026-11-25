/* ServisFlow prototip — ortak bileşenler, etiketler, kabuk (sidebar/topbar/alt menü), dialog ve toast. */
(function () {
  const D = window.DB;
  const esc = s => String(s == null ? "" : s).replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

  /* ── Etiketler: enum → Türkçe metin + rozet tonu + ikon ── */
  const L = window.LABELS = {
    trip: {
      PLANNED: { t: "Planlandı", c: "b-info", i: "calendar" },
      IN_PROGRESS: { t: "Yolda", c: "b-live", i: "live" },
      COMPLETED: { t: "Tamamlandı", c: "b-muted", i: "checkCircle" },
      CANCELLED: { t: "İptal edildi", c: "b-danger", i: "ban" },
    },
    roster: {
      NONE: { t: "İşlem yok", c: "b-none", i: "clock" },
      BOARDED: { t: "Bindi", c: "b-ok", i: "board" },
      DROPPED_OFF: { t: "İndi", c: "b-info", i: "drop" },
      ABSENT: { t: "Gelmedi", c: "b-danger", i: "absent" },
    },
    dir: { MORNING: { t: "Gidiş (sabah)", s: "Gidiş", i: "sun" }, AFTERNOON: { t: "Dönüş (akşam)", s: "Dönüş", i: "moon" } },
    incident: {
      DELAY: { t: "Gecikme", c: "b-warn", i: "delay" },
      BREAKDOWN: { t: "Arıza", c: "b-danger", i: "wrench" },
      ROAD_CLOSURE: { t: "Yol kapanması", c: "b-warn", i: "roadblock" },
      CANCELLATION_REQUEST: { t: "İptal talebi", c: "b-danger", i: "ban" },
    },
    incStatus: { RECORDED: { t: "Kaydedildi", c: "b-muted", i: "check" }, OPEN: { t: "Karar bekliyor", c: "b-warn", i: "clock" }, APPROVED: { t: "Onaylandı", c: "b-ok", i: "checkCircle" }, REJECTED: { t: "Reddedildi", c: "b-danger", i: "x" } },
    req: { PENDING: { t: "Bekliyor", c: "b-warn", i: "clock" }, APPROVED: { t: "Onaylandı", c: "b-ok", i: "checkCircle" }, REJECTED: { t: "Reddedildi", c: "b-danger", i: "x" } },
    kind: { CORRECTION: "Düzeltme", REVIEW: "İnceleme" },
    contract: { ACTIVE: { t: "Aktif", c: "b-ok", i: "checkCircle" }, PENDING_APPROVAL: { t: "Onay bekliyor", c: "b-warn", i: "clock" }, CANCELLED: { t: "İptal / ret", c: "b-danger", i: "ban" }, EXPIRED: { t: "Süresi doldu", c: "b-muted", i: "history" } },
    reg: { PENDING_EMAIL_VERIFICATION: { t: "E-posta bekleniyor", c: "b-muted", i: "mail" }, EMAIL_VERIFIED: { t: "Onay bekliyor", c: "b-warn", i: "clock" }, APPROVED: { t: "Onaylandı", c: "b-ok", i: "checkCircle" }, REJECTED: { t: "Reddedildi", c: "b-danger", i: "x" } },
    pay: { COMPLETED: { t: "Tamamlandı", c: "b-ok", i: "checkCircle" }, CANCELLED: { t: "İptal", c: "b-danger", i: "ban" } },
    method: { CASH: "Nakit", BANK_TRANSFER: "Havale/EFT", CARD: "Kart", OTHER: "Diğer" },
    role: { ADMIN: "Yönetici", DRIVER: "Şoför", HOSTESS: "Hostes", PARENT: "Veli", STUDENT: "Öğrenci" },
    active: { true: { t: "Aktif", c: "b-ok", i: "check" }, false: { t: "Pasif", c: "b-muted", i: "ban" } },
  };
  const badge = (m, extra) => m ? '<span class="badge ' + m.c + '">' + (m.c === "b-live" ? '<span class="pulse"></span>' : icon(m.i, 14)) + esc(m.t) + (extra ? " " + esc(extra) : "") + "</span>" : "";
  const noticeBadge = () => '<span class="badge b-notice">' + icon("hand", 14) + "Veli: binmeyecek</span>";
  const newApi = txt => '<span class="tag-new-api" title="Mevcut API bu veriyi döndürmüyor — Sprint 2 öncesi API kararı gerekir">' + icon("alert", 12) + esc(txt || "Yeni API gerekir") + "</span>";

  /* ── Biçimlendirme (tr-TR, Europe/Istanbul) ── */
  const money = n => new Intl.NumberFormat("tr-TR", { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(n) + " TL";
  const date = iso => { if (!iso) return "—"; const [y, m, d] = iso.slice(0, 10).split("-"); return d + "." + m + "." + y; };
  const time = iso => iso ? iso.slice(11, 16) : "—";
  const dt = iso => iso ? date(iso) + " " + time(iso) : "—";
  const period = p => { const n = ["Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"]; const [y, m] = p.split("-"); return n[+m - 1] + " " + y; };
  const rel = iso => { if (!iso) return ""; if (iso.slice(0, 10) !== D.TODAY) return dt(iso); return "Bugün " + time(iso); };
  const initials = n => n.split(" ").map(x => x[0]).slice(0, 2).join("").toLocaleUpperCase("tr");
  const plate = p => '<span class="plate"><span>' + esc(p) + "</span></span>";
  const ago = s => s == null ? "—" : s < 60 ? s + " sn önce" : s < 3600 ? Math.round(s / 60) + " dk önce" : Math.round(s / 3600) + " sa önce";

  /* ── Personalar (prototipte "giriş yapmış kullanıcı") ── */
  const PERSONAS = {
    admin: { role: "ADMIN", userId: 1 },
    driver: { role: "DRIVER", userId: 11, driverId: 1 },
    hostess: { role: "HOSTESS", userId: 21, hostessId: 1 },
    parent: { role: "PARENT", userId: 31, parentId: 1 },
    student: { role: "STUDENT", userId: 41, studentId: 1 },
  };

  /* ── Menüler (önerilen bilgi mimarisi; mevcut lib/rbac.js ile farkları SCREEN_MATRIX.md'de) ── */
  const pendingCounts = () => ({
    corr: D.db.corrections.filter(c => c.status === "PENDING").length,
    stop: D.db.stopChanges.filter(c => c.status === "PENDING").length,
    enr: D.db.contracts.filter(c => c.status === "PENDING_APPROVAL").length,
    reg: D.db.regRequests.filter(c => c.status === "EMAIL_VERIFIED").length,
    cancel: D.db.incidents.filter(i => i.type === "CANCELLATION_REQUEST" && i.status === "OPEN").length,
  });
  function menus(role) {
    const c = pendingCounts();
    const M = {
      admin: [
        { g: "Operasyon", items: [["operasyon", "Günlük Operasyon", "ops"], ["seferler", "Seferler", "bus", c.cancel], ["konum", "Canlı Konum", "live"]] },
        { g: "Talepler ve onaylar", items: [["talepler", "Talep Merkezi", "inbox", c.corr + c.stop + c.enr + c.reg], ["yoklama-duzeltmeleri", "Yoklama Düzeltmeleri", "history", c.corr], ["durak-talepleri", "Durak Değişiklikleri", "stop", c.stop], ["ogrenci-basvurulari", "Öğrenci Başvuruları", "student", c.enr], ["kayit-talepleri", "Hesap Kayıt Talepleri", "mail", c.reg]] },
        { g: "Yönetim", items: [["ogrenciler", "Öğrenciler", "student"], ["veliler", "Veliler", "parent"], ["soforler", "Şoförler", "driver"], ["hostesler", "Hostesler", "hostess"], ["araclar", "Araçlar", "vehicle"], ["okullar", "Okullar", "school"], ["guzergahlar", "Güzergâhlar ve Duraklar", "route"], ["atamalar", "Öğrenci Atamaları", "link"], ["kullanicilar", "Kullanıcılar", "users"]] },
        { g: "Finans", items: [["sozlesmeler", "Sözleşmeler", "contract"], ["tahakkuklar", "Tahakkuklar", "receipt"], ["odemeler", "Ödemeler", "wallet"], ["raporlar", "Raporlar", "chart"]] },
        { g: "İletişim ve ayarlar", items: [["bildirimler", "Bildirimler", "bell"], ["toplu-bildirim", "Toplu Bildirim", "megaphone"], ["ayarlar", "Kurum Ayarları", "settings"]] },
      ],
      driver: [{ g: "Saha", items: [["bugun", "Bugün", "home"], ["yoklama", "Yoklama", "checkCircle"], ["guzergahim", "Güzergâhım", "route"], ["aracim", "Aracım", "vehicle"], ["duzeltme-taleplerim", "Düzeltme Taleplerim", "history"], ["bildirimler", "Bildirimler", "bell"], ["profil", "Profil", "user"]] }],
      hostess: [{ g: "Saha", items: [["bugun", "Bugün", "home"], ["yoklama", "Yoklama", "checkCircle"], ["guzergahim", "Güzergâhım", "route"], ["duzeltme-taleplerim", "Düzeltme Taleplerim", "history"], ["bildirimler", "Bildirimler", "bell"], ["profil", "Profil", "user"]] }],
      parent: [{ g: "Servis", items: [["servisim", "Bugünkü Servis", "bus"], ["yoklama-gecmisi", "Yoklama Geçmişi", "history"], ["taleplerim", "Taleplerim", "inbox"]] }, { g: "Hesap", items: [["odemeler", "Ödemeler ve Ekstre", "wallet"], ["cocuklarim", "Çocuklarım", "student"], ["ogrenci-ekle", "Öğrenci Ekle", "plus"], ["bildirimler", "Bildirimler", "bell"], ["profil", "Profil", "user"]] }],
      student: [{ g: "Öğrenci", items: [["servisim", "Bugünkü Servisim", "bus"], ["guzergahim", "Güzergâhım", "route"], ["yoklama-gecmisi", "Yoklama Geçmişim", "history"], ["bildirimler", "Bildirimler", "bell"], ["profil", "Bilgilerim", "user"]] }],
    };
    return M[role] || [];
  }
  const BOTTOM = {
    admin: ["operasyon", "seferler", "talepler", "bildirimler"],
    driver: ["bugun", "yoklama", "guzergahim", "duzeltme-taleplerim"],
    hostess: ["bugun", "yoklama", "guzergahim", "duzeltme-taleplerim"],
    parent: ["servisim", "yoklama-gecmisi", "taleplerim", "odemeler"],
    student: ["servisim", "guzergahim", "yoklama-gecmisi", "bildirimler"],
  };
  const SHORT = { operasyon: "Operasyon", seferler: "Seferler", talepler: "Talepler", bildirimler: "Bildirim", bugun: "Bugün", yoklama: "Yoklama", guzergahim: "Güzergâh", "duzeltme-taleplerim": "Talepler", servisim: "Servis", "yoklama-gecmisi": "Yoklama", taleplerim: "Talepler", odemeler: "Ödemeler" };

  /* ── Kabuk ── */
  function unread(userId) { return D.db.notifications.filter(n => n.recipientUserId === userId && !n.read).length; }
  function shell(ctx, page) {
    const p = PERSONAS[ctx.role], u = D.user(p.userId), groups = menus(ctx.role);
    const all = groups.flatMap(g => g.items);
    const navHtml = groups.map(g => '<div class="nav-group"><div class="nav-group-title">' + esc(g.g) + "</div>" + g.items.map(([k, t, i, n]) =>
      '<a class="nav-link' + (ctx.screen === k ? " active" : "") + '" href="#/' + ctx.role + "/" + k + '">' + icon(i, 18) + "<span>" + esc(t) + "</span>" + (n ? '<span class="count">' + n + "</span>" : "") + "</a>").join("") + "</div>").join("");
    const ur = unread(u.id);
    const bottom = BOTTOM[ctx.role].map(k => { const it = all.find(x => x[0] === k); return '<a class="' + (ctx.screen === k ? "active" : "") + '" href="#/' + ctx.role + "/" + k + '">' + icon(it[2], 22) + (it[3] ? '<span class="count">' + it[3] + "</span>" : "") + "<span>" + SHORT[k] + "</span></a>"; }).join("")
      + '<a href="javascript:void 0" data-act="open-menu" class="' + (BOTTOM[ctx.role].includes(ctx.screen) ? "" : "active") + '">' + icon("menu", 22) + "<span>Daha fazla</span></a>";
    const sheet = '<div class="m-menu" id="m-menu"><div class="scrim" data-act="close-menu"></div><div class="sheet"><div class="grabber"></div>' +
      '<div class="row" style="padding:4px 8px 8px"><div class="avatar">' + initials(u.fullName) + '</div><div><b>' + esc(u.fullName) + '</b><div class="xs muted">' + L.role[u.role] + "</div></div></div>" +
      groups.map(g => "<h4>" + esc(g.g) + "</h4>" + g.items.map(([k, t, i, n]) => '<a class="' + (ctx.screen === k ? "active" : "") + '" href="#/' + ctx.role + "/" + k + '">' + icon(i, 20) + esc(t) + (n ? ' <span class="badge b-warn" style="margin-left:auto">' + n + "</span>" : "") + "</a>").join("")).join("") +
      '<h4>Oturum</h4><a href="#/public/giris">' + icon("logout", 20) + "Çıkış yap</a></div></div>";
    return '<div class="app">' +
      '<aside class="sidebar"><a class="brand" href="#/' + ctx.role + "/" + all[0][0] + '"><span class="brand-mark">' + icon("bus", 20) + '</span><span><span class="brand-name">ServisFlow</span><br><span class="brand-sub">' + L.role[u.role] + " paneli</span></span></a>" + navHtml +
      '<div class="sidebar-foot"><a href="#/public/giris" style="color:#c9d2da;text-decoration:none" class="row">' + icon("logout", 16) + " Çıkış yap</a></div></aside>" +
      '<div class="main">' +
      '<header class="topbar"><div><div class="crumbs">' + esc(page.crumb || L.role[u.role]) + '</div><div class="page-title">' + esc(page.title) + '</div></div><div class="right">' +
      (page.topRight || "") + '<span class="today-chip">' + icon("calendar", 15) + "18 Kasım 2026, Çarşamba</span>" +
      '<a class="icon-btn" href="#/' + ctx.role + '/bildirimler" aria-label="Bildirimler">' + icon("bell", 19) + (ur ? '<span class="dot">' + ur + "</span>" : "") + "</a>" +
      '<a class="user-chip" href="#/' + ctx.role + '/profil"><span class="avatar">' + initials(u.fullName) + '</span><span class="who"><b>' + esc(u.fullName) + "</b><span>" + L.role[u.role] + "</span></span></a></div></header>" +
      '<header class="m-top">' + (page.back ? '<a class="icon-btn" href="' + page.back + '" aria-label="Geri">' + icon("chevL", 20) + "</a>" : '<span class="brand-mark" style="width:32px;height:32px">' + icon("bus", 18) + "</span>") +
      '<div><div class="t">' + esc(page.mTitle || page.title) + '</div><div class="st">' + esc(page.mSub || "18.11.2026 · " + L.role[u.role]) + '</div></div><div class="right"><a class="icon-btn" href="#/' + ctx.role + '/bildirimler" aria-label="Bildirimler">' + icon("bell", 19) + (ur ? '<span class="dot">' + ur + "</span>" : "") + "</a></div></header>" +
      '<main class="content' + (page.narrow ? " narrow" : "") + '" id="content">' + page.html + "</main>" +
      '<nav class="bottom-nav" aria-label="Alt menü">' + bottom + "</nav>" + sheet +
      "</div></div>";
  }

  /* ── Ekran durumları: yükleniyor / boş / hata / yetki yok ── */
  function stateBlock(kind, meta) {
    meta = meta || {};
    if (kind === "loading") return '<div class="card card-pad stack-lg" aria-busy="true"><div class="sk" style="height:22px;width:40%"></div>' + [1, 2, 3, 4, 5].map(() => '<div class="row" style="gap:12px"><div class="sk" style="width:40px;height:40px;border-radius:12px"></div><div style="flex:1" class="stack"><div class="sk" style="height:14px;width:70%"></div><div class="sk" style="height:12px;width:45%"></div></div></div>').join("") + '<span class="sr-only">Yükleniyor</span></div>';
    if (kind === "empty") return '<div class="card"><div class="state"><div class="ill">' + icon(meta.emptyIcon || "inbox", 30) + "</div><h3>" + esc(meta.emptyTitle || "Henüz kayıt yok") + "</h3><p>" + esc(meta.emptyText || "Bu listede gösterilecek kayıt bulunmuyor.") + "</p>" + (meta.emptyAction || "") + "</div></div>";
    if (kind === "error") return '<div class="card"><div class="state err"><div class="ill">' + icon("alert", 30) + "</div><h3>Veriler yüklenemedi</h3><p>Sunucuya ulaşılamadı veya beklenmeyen bir hata oluştu. Bağlantınızı kontrol edip tekrar deneyin. Sorun sürerse hata kodunu yöneticinize iletin.</p><div class='xs muted mono'>Hata kodu: 503 · istek no 7f3a-21c</div><button class='btn btn-primary' data-act='retry'>" + icon("refresh", 16) + "Tekrar dene</button></div></div>";
    if (kind === "forbidden") return '<div class="card"><div class="state forbid"><div class="ill">' + icon("lock", 30) + "</div><h3>Bu sayfaya erişim yetkiniz yok</h3><p>Bu işlem rolünüz için açık değil ya da kayıt sizin kapsamınızda değil. Bir hata olduğunu düşünüyorsanız kurum yöneticisiyle iletişime geçin.</p><a class='btn btn-secondary' href='#/" + (window.APP && window.APP.ctx ? window.APP.ctx.role : "public") + "'>Ana sayfaya dön</a></div></div>";
    return "";
  }

  /* ── Form alanları ── */
  function field(o) {
    const id = "f-" + o.name;
    let control;
    if (o.type === "select") control = '<select class="input" id="' + id + '" name="' + o.name + '">' + (o.placeholder ? '<option value="">' + esc(o.placeholder) + "</option>" : "") + o.options.map(op => '<option value="' + esc(op[0]) + '"' + (String(op[0]) === String(o.value) ? " selected" : "") + ">" + esc(op[1]) + "</option>").join("") + "</select>";
    else if (o.type === "textarea") control = '<textarea class="input' + (o.error ? " invalid" : "") + '" id="' + id + '" name="' + o.name + '" placeholder="' + esc(o.placeholder || "") + '" maxlength="' + (o.max || 500) + '">' + esc(o.value || "") + "</textarea>";
    else control = '<input class="input' + (o.error ? " invalid" : "") + '" id="' + id + '" name="' + o.name + '" type="' + (o.type || "text") + '" value="' + esc(o.value || "") + '" placeholder="' + esc(o.placeholder || "") + '"' + (o.readonly ? " readonly" : "") + ">";
    return '<div class="field' + (o.full ? " full" : "") + '"><label for="' + id + '">' + esc(o.label) + (o.req ? ' <span class="req">*</span>' : "") + "</label>" + control + (o.error ? '<span class="err">' + esc(o.error) + "</span>" : "") + (o.help ? '<span class="help">' + esc(o.help) + "</span>" : "") + "</div>";
  }

  /* ── Dialog ── */
  let dialogHandler = null;
  function dialog(o) {
    const root = document.getElementById("dialog-root");
    const tone = { danger: ["var(--danger-bg)", "var(--danger)"], warn: ["var(--warn-bg)", "var(--warn)"], ok: ["var(--ok-bg)", "var(--ok)"], info: ["var(--info-bg)", "var(--info)"], notice: ["var(--notice-bg)", "var(--notice)"] }[o.tone || "info"];
    root.innerHTML = '<div class="scrim" data-act="dlg-close"></div><div class="dialog' + (o.wide ? " wide" : "") + '" role="dialog" aria-modal="true" aria-labelledby="dlg-t"><form id="dlg-form" novalidate>' +
      '<div class="d-head">' + (o.icon ? '<span class="d-icon" style="background:' + tone[0] + ";color:" + tone[1] + '">' + icon(o.icon, 22) + "</span>" : "") + '<div><h3 id="dlg-t">' + esc(o.title) + "</h3>" + (o.sub ? '<div class="small muted">' + o.sub + "</div>" : "") + '</div><button type="button" class="icon-btn x" data-act="dlg-close" aria-label="Kapat">' + icon("x", 18) + "</button></div>" +
      '<div class="d-body">' + (o.body || "") + '<div class="err" id="dlg-err" style="display:none"></div></div>' +
      '<div class="d-foot"><button type="button" class="btn btn-secondary" data-act="dlg-close">' + esc(o.cancel || "Vazgeç") + '</button><button type="submit" class="btn ' + (o.okClass || "btn-primary") + '">' + (o.okIcon ? icon(o.okIcon, 16) : "") + esc(o.ok || "Kaydet") + "</button></div></form></div>";
    root.classList.add("open");
    dialogHandler = o.onSubmit;
    const f = root.querySelector("input:not([readonly]),select,textarea"); if (f) setTimeout(() => f.focus(), 30);
  }
  function closeDialog() { const r = document.getElementById("dialog-root"); r.classList.remove("open"); r.innerHTML = ""; dialogHandler = null; }
  document.addEventListener("submit", e => {
    if (e.target.id !== "dlg-form") return;
    e.preventDefault();
    const data = Object.fromEntries(new FormData(e.target).entries());
    const errEl = document.getElementById("dlg-err");
    try {
      const res = dialogHandler && dialogHandler(data);
      if (res === false) return;
      if (typeof res === "string") { errEl.textContent = res; errEl.style.display = "block"; return; }
      closeDialog(); window.APP.render();
    } catch (ex) { errEl.textContent = ex.message; errEl.style.display = "block"; }
  });

  function toast(msg, kind) {
    const r = document.getElementById("toast-root");
    const el = document.createElement("div"); el.className = "toast " + (kind || "ok"); el.setAttribute("role", "status");
    el.innerHTML = icon(kind === "err" ? "alert" : "checkCircle", 18) + "<span>" + esc(msg) + "</span>";
    r.appendChild(el); setTimeout(() => el.remove(), 3600);
  }

  /* ── Stilize harita (gerçek harita kütüphanesi Sprint 2 kararı; bkz. NEXT_SPRINT_HANDOFF.md) ── */
  function mapPanel(o) {
    o = o || {};
    const stops = o.stops || [];
    const pts = [[40, 230], [140, 190], [230, 210], [320, 150], [420, 160], [520, 90], [610, 70]];
    const path = "M" + pts.map(p => p.join(",")).join(" L");
    const sp = stops.map((s, i) => { const k = stops.length === 1 ? 0.34 : i / (stops.length - 1); const idx = Math.min(pts.length - 1, Math.round(k * (pts.length - 1))); return { s, x: pts[idx][0], y: pts[idx][1] }; });
    const prog = o.loc && !o.loc.isOffline ? (o.loc.progress || 0.5) : (o.loc ? (o.loc.progress || 0.5) : null);
    let bus = "";
    if (prog != null) { const fi = prog * (pts.length - 1), i0 = Math.floor(fi), i1 = Math.min(pts.length - 1, i0 + 1), f = fi - i0; const x = pts[i0][0] + (pts[i1][0] - pts[i0][0]) * f, y = pts[i0][1] + (pts[i1][1] - pts[i0][1]) * f; bus = '<g transform="translate(' + x + "," + y + ')"' + (o.loc.isOffline ? ' opacity=".55"' : "") + '><circle r="20" fill="' + (o.loc.isOffline ? "#9aa3ad" : "#f0b20a") + '" opacity=".25"/><circle r="13" fill="' + (o.loc.isOffline ? "#9aa3ad" : "#f0b20a") + '" stroke="#15202b" stroke-width="2"/><path d="M-6 -4h12v7h-12z M-6 0h12" stroke="#15202b" stroke-width="1.6" fill="none"/></g>'; }
    const base = '<svg class="base" viewBox="0 0 660 300" preserveAspectRatio="xMidYMid slice" aria-hidden="true">' +
      '<rect width="660" height="300" fill="#e7ede3"/>' +
      '<path d="M0 120 C120 100 200 140 330 110 S560 60 660 80" stroke="#fff" stroke-width="14" fill="none"/><path d="M180 0 L220 300" stroke="#fff" stroke-width="10"/><path d="M460 0 L430 300" stroke="#fff" stroke-width="10"/><path d="M0 260 L660 250" stroke="#fff" stroke-width="8"/>' +
      '<rect x="250" y="20" width="120" height="60" rx="8" fill="#d5e3cf"/><rect x="520" y="180" width="110" height="80" rx="8" fill="#d5e3cf"/><path d="M60 40 h80 v40 h-80z" fill="#dfe4dc"/>' +
      '<path d="' + path + '" stroke="#0d6e5b" stroke-width="5" fill="none" stroke-linecap="round" stroke-linejoin="round" stroke-dasharray="' + (o.planned ? "2 9" : "0") + '"/>' +
      sp.map(p => '<g transform="translate(' + p.x + "," + p.y + ')"><circle r="' + (p.s.mine ? 9 : 6) + '" fill="#fff" stroke="' + (p.s.mine ? "#5c46b0" : "#0d6e5b") + '" stroke-width="' + (p.s.mine ? 4 : 3) + '"/></g>').join("") +
      bus + "</svg>";
    if (o.empty) return '<div class="map empty" style="min-height:' + (o.h || 280) + 'px">' + base + '<div class="msg"><div class="row" style="justify-content:center;color:var(--ink-3)">' + icon(o.emptyIcon || "signalOff", 28) + '</div><b>' + esc(o.emptyTitle) + '</b><div class="small muted">' + esc(o.emptyText) + "</div></div></div>";
    const stamp = o.loc ? '<div class="stamp">' + (o.loc.isOffline ? '<span class="badge b-muted">' + icon("signalOff", 14) + "Bağlantı yok</span>" : '<span class="badge b-live"><span class="pulse"></span>Canlı</span>') + "<b>Son konum: " + time(o.loc.recordedAt) + '</b><span class="muted">' + (o.loc.isOffline ? "90 sn'den uzun süredir güncellenmedi" : "Güncellendi: " + ago(o.loc.staleSeconds)) + "</span></div>" : "";
    return '<div class="map" style="min-height:' + (o.h || 280) + 'px">' + base + '<div class="overlay">' + stamp + (o.overlay || "") + "</div>" +
      '<div class="legend"><span><i style="background:#f0b20a;border:2px solid #15202b"></i>Servis (son konum)</span><span><i style="background:#fff;border:3px solid #0d6e5b"></i>Durak</span>' + (sp.some(p => p.s.mine) ? '<span><i style="background:#fff;border:3px solid #5c46b0"></i>Sizin durağınız</span>' : "") + "</div></div>";
  }

  /* ── Liste/tablo şablonu (yönetim ekranlarının ortak şablonu) ── */
  function table(cols, rows, o) {
    o = o || {};
    return '<div class="table-wrap"><table class="t responsive"><thead><tr>' + cols.map(c => "<th" + (c.right ? ' style="text-align:right"' : "") + ">" + esc(c.h) + "</th>").join("") + "</tr></thead><tbody>" +
      rows.map(r => "<tr" + (o.href ? ' class="clickable" data-href="' + o.href(r) + '"' : "") + ">" + cols.map(c => "<td" + (c.main ? ' class="cell-main"' : c.actions ? ' class="actions"' : "") + ' data-l="' + esc(c.h) + '"' + (c.right ? ' style="text-align:right"' : "") + ">" + c.v(r) + "</td>").join("") + "</tr>").join("") + "</tbody></table></div>";
  }
  function filters(list) { return '<div class="filters">' + list.join("") + "</div>"; }
  function pager(n) { return '<div class="card-foot small muted"><span>' + n + " kayıt · sayfa 1/1</span><span style=\"margin-left:auto\" class=\"row\"><button class=\"btn btn-ghost btn-sm\" disabled>" + icon("chevL", 14) + "</button><button class=\"btn btn-ghost btn-sm\" disabled>" + icon("chevR", 14) + "</button></span></div>"; }

  window.UI = { esc, L, badge, noticeBadge, newApi, money, date, time, dt, period, rel, initials, plate, ago, PERSONAS, menus, shell, stateBlock, field, dialog, closeDialog, toast, mapPanel, table, filters, pager, unread, pendingCounts };
})();
