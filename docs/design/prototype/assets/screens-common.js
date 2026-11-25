/* Ortak ekranlar: giriş/kayıt/şifre akışları, bildirimler, profil, ekran dizini ve sefer bileşenleri. */
(function () {
  const D = window.DB, U = window.UI, { esc, L, badge, icon: _i } = Object.assign({}, U, { icon: window.icon });
  const S = window.SCREENS = window.SCREENS || { public: {}, admin: {}, driver: {}, hostess: {}, parent: {}, student: {}, shared: {} };
  const A = window.ACTIONS = window.ACTIONS || {};
  const ic = window.icon;

  /* ───────── Herkese açık sayfalar ───────── */
  function authLayout(title, inner) {
    return { title, html: '<div class="auth"><div class="aside"><a class="brand" style="padding:0" href="#/index"><span class="brand-mark">' + ic("bus", 20) + '</span><span class="brand-name">ServisFlow</span></a>' +
      "<h2>Okul servisinizin günü tek ekranda: sefer, yoklama ve veli bilgilendirmesi.</h2>" +
      '<div class="roles"><div><b>Yönetici</b>Günlük operasyon, talepler, finans</div><div><b>Şoför ve hostes</b>Aktif sefer, yoklama, olay bildirimi</div><div><b>Veli</b>Bugünkü servis, bildirim, ödemeler</div><div><b>Öğrenci</b>Servis saati, durak, geçmiş</div></div>' +
      '<p class="small" style="color:#95a1ad;margin-top:auto">Hesaplar kurum yöneticisi tarafından açılır veya kayıt talebiyle onaylanır.</p></div>' +
      '<div class="form-side"><div class="panel">' + inner + "</div></div></div>" };
  }
  S.public.giris = () => authLayout("Giriş", '<span class="brand-mark show-m">' + ic("bus", 20) + "</span><h1>Giriş yap</h1><p class='muted'>Kurumunuzun size verdiği e-posta ve şifreyle giriş yapın.</p>" +
    '<form class="stack-lg" onsubmit="event.preventDefault();location.hash=\'#/index\'">' + U.field({ name: "email", label: "E-posta", type: "email", value: "elif.korkmaz@ornek-posta.test", req: true }) + U.field({ name: "password", label: "Şifre", type: "password", value: "••••••••", req: true }) +
    '<div class="between"><label class="check"><input type="checkbox"> Beni hatırla</label><a href="#/public/sifremi-unuttum" class="small">Şifremi unuttum</a></div>' +
    '<button class="btn btn-primary btn-lg btn-block">Giriş yap</button></form>' +
    '<div class="alert a-danger">' + ic("alert", 18) + "<div><b>Hata durumu örneği</b>E-posta veya şifre hatalı. 5 hatalı denemeden sonra kısa süreli bekleme uygulanır.</div></div>" +
    '<div class="divider"></div><p class="small muted">Hesabınız yok mu? <a href="#/public/kayit-talebi">Kayıt talebi bırakın</a>. Sosyal giriş düğmeleri yalnızca sağlayıcı yapılandırıldığında (<span class="mono">GET /api/auth/social/providers</span>) gösterilir.</p>');
  S.public["kayit-talebi"] = () => authLayout("Kayıt talebi", "<h1>Kayıt talebi</h1><p class='muted'>Talebiniz e-posta doğrulamasından sonra kurum yöneticisine iletilir. Rolünüzü yönetici belirler.</p>" +
    '<form class="stack-lg" onsubmit="event.preventDefault();location.hash=\'#/public/e-posta-dogrula\'"><div class="form-grid">' + U.field({ name: "firstName", label: "Ad", req: true }) + U.field({ name: "lastName", label: "Soyad", req: true }) + U.field({ name: "email", label: "E-posta", type: "email", req: true, full: true }) + U.field({ name: "password", label: "Şifre", type: "password", req: true, help: "En az 8 karakter" }) + U.field({ name: "passwordConfirm", label: "Şifre tekrar", type: "password", req: true }) + "</div>" +
    '<button class="btn btn-primary btn-lg btn-block">Talep gönder</button></form><div class="alert a-info">' + ic("info", 18) + "<div>Gönderdikten sonra her durumda “Talebiniz alındı” ekranı gösterilir (sunucu her zaman 202 döner; hesabın var olup olmadığı açıklanmaz).</div></div>");
  S.public["e-posta-dogrula"] = () => authLayout("E-posta doğrulama", '<div class="state" style="padding:0;align-items:flex-start;text-align:left"><div class="ill" style="background:var(--ok-bg);color:var(--ok)">' + ic("mail", 30) + "</div><h1>E-postanızı kontrol edin</h1><p>Doğrulama bağlantısı 24 saat geçerlidir. Bağlantıya tıkladığınızda talebiniz yönetici onayına geçer; onaylanınca giriş yapabilirsiniz.</p></div>" +
    '<div class="card card-pad stack"><b>Bağlantı açıldığında</b><div class="alert a-ok">' + ic("checkCircle", 18) + "<div><b>E-posta doğrulandı</b>Talebiniz yönetici onayı bekliyor.</div></div><div class='alert a-danger'>" + ic("alert", 18) + "<div><b>Bağlantı geçersiz veya süresi dolmuş</b>Yeni bir kayıt talebi oluşturun.</div></div></div><a href='#/public/giris'>Giriş sayfasına dön</a>");
  S.public["sifremi-unuttum"] = () => authLayout("Şifremi unuttum", "<h1>Şifremi unuttum</h1><p class='muted'>Hesabınıza kayıtlı e-postayı yazın. Hesap varsa sıfırlama bağlantısı gönderilir.</p>" +
    '<form class="stack-lg" onsubmit="event.preventDefault();location.hash=\'#/public/sifre-sifirla\'">' + U.field({ name: "email", label: "E-posta", type: "email", req: true }) + '<button class="btn btn-primary btn-lg btn-block">Bağlantı gönder</button></form><div class="alert a-muted">' + ic("info", 18) + "<div>Güvenlik gereği sonuç ekranı her zaman aynıdır: “Hesap varsa e-posta gönderildi.”</div></div><a href='#/public/giris'>Girişe dön</a>");
  S.public["sifre-sifirla"] = () => authLayout("Şifre sıfırla", "<h1>Yeni şifre belirleyin</h1>" +
    '<form class="stack-lg" onsubmit="event.preventDefault();location.hash=\'#/public/giris\'">' + U.field({ name: "p1", label: "Yeni şifre", type: "password", req: true, help: "En az 8 karakter" }) + U.field({ name: "p2", label: "Yeni şifre tekrar", type: "password", req: true, error: "Şifreler eşleşmiyor (doğrulama hatası örneği)" }) + '<button class="btn btn-primary btn-lg btn-block">Şifreyi kaydet</button></form><p class="small muted">Bağlantı tek kullanımlıktır. Şifre değişince diğer tüm oturumlar kapatılır.</p>');

  /* ───────── Bildirimler (tüm roller) ───────── */
  S.shared.bildirimler = ctx => {
    const p = APP.persona(), list = D.db.notifications.filter(n => n.recipientUserId === p.userId);
    const unread = list.filter(n => !n.read).length;
    const filter = ctx.q.f || "all";
    const shown = filter === "unread" ? list.filter(n => !n.read) : list;
    const html = (ctx.role === "admin" ? '<div class="alert a-muted">' + ic("megaphone", 18) + '<div>Toplu duyuru göndermek için <a href="#/admin/toplu-bildirim">Toplu Bildirim</a> ekranını kullanın.</div></div>' : "") +
      '<div class="card"><div class="card-head"><div class="seg"><a class="' + (filter === "all" ? "on" : "") + '" href="#/' + ctx.role + '/bildirimler">Tümü</a><a class="' + (filter === "unread" ? "on" : "") + '" href="#/' + ctx.role + '/bildirimler?f=unread">Okunmamış ' + (unread ? "(" + unread + ")" : "") + '</a></div><div class="right"><button class="btn btn-ghost btn-sm" data-act="read-all">' + ic("check", 15) + "Tümünü okundu işaretle</button></div></div>" +
      (shown.length ? '<div class="list">' + shown.map(n => '<a class="li' + (n.read ? "" : " unread") + '" href="javascript:void 0" data-act="read-one" data-id="' + n.id + '"><span class="avatar" style="background:' + (n.read ? "var(--surface-sunken)" : "var(--brand-soft)") + '">' + ic(/iptal/i.test(n.title) ? "ban" : /gecik/i.test(n.title) ? "delay" : /onay/i.test(n.title) ? "checkCircle" : /redd/i.test(n.title) ? "x" : "bell", 18) + '</span><span class="grow"><span class="title">' + esc(n.title) + '</span><br><span class="meta">' + esc(n.body) + '</span></span><span class="xs muted" style="white-space:nowrap">' + U.rel(n.createdAt) + "</span></a>").join("") + "</div>" : U.stateBlock("empty", { emptyIcon: "bell", emptyTitle: "Okunmamış bildirim yok", emptyText: "Yeni bildirimler burada görünür." })) +
      "</div>" + '<p class="xs muted">Kaynak: <span class="mono">GET /api/notifications</span>, <span class="mono">POST /api/notifications/{id}/read</span>. “Tümünü okundu işaretle” mevcut API ile kayıt başına istek atar (toplu uç yok).</p>';
    return { title: "Bildirimler", html, narrow: true, meta: { emptyIcon: "bell", emptyTitle: "Bildiriminiz yok", emptyText: "Sefer, talep ve ödeme gelişmeleri burada listelenir." } };
  };
  A["read-one"] = el => { D.api.markRead(+el.dataset.id); APP.render(); };
  A["read-all"] = () => { D.api.markAllRead(APP.persona().userId); U.toast("Tüm bildirimler okundu işaretlendi"); APP.render(); };

  /* ───────── Profil (tüm roller) ───────── */
  S.shared.profil = ctx => {
    const p = APP.persona(), u = D.user(p.userId);
    let extra = "";
    if (ctx.role === "driver") { const d = D.by(D.db.drivers, p.driverId); extra = '<dt>Ehliyet no</dt><dd class="mono">' + esc(d.licenseNo) + "</dd>"; }
    if (ctx.role === "parent") { const pr = D.by(D.db.parents, p.parentId); extra = "<dt>Telefon</dt><dd>" + esc(pr.phone) + "</dd><dt>T.C. kimlik</dt><dd class='mono'>" + esc(pr.tcNo || "—") + "</dd>"; }
    if (ctx.role === "student") { const s = D.student(p.studentId); extra = "<dt>Okul</dt><dd>" + esc(s.schoolName) + "</dd><dt>Sınıf</dt><dd>" + esc(s.grade) + "</dd><dt>Doğum tarihi</dt><dd>" + U.date(s.birthDate) + "</dd><dt>Sağlık notu</dt><dd>" + esc(s.healthNote || "—") + "</dd>"; }
    const src = { admin: "GET /api/auth/me", driver: "GET /api/drivers/me", hostess: "GET /api/hostesses/me", parent: "GET /api/parents/me", student: "GET /api/students/{id}" }[ctx.role];
    const html = '<div class="grid g-2"><div class="card"><div class="card-head"><span class="avatar" style="width:48px;height:48px;font-size:16px">' + U.initials(u.fullName) + '</span><div><h3>' + esc(u.fullName) + '</h3><div class="sub">' + L.role[u.role] + '</div></div></div><div class="card-pad"><dl class="dl"><dt>E-posta</dt><dd>' + esc(u.email) + "</dd>" + (u.phone && ctx.role !== "parent" ? "<dt>Telefon</dt><dd>" + esc(u.phone) + "</dd>" : "") + extra + '</dl><p class="xs muted" style="margin-top:12px">Bilgiler salt okunurdur; değişiklik için kurum yöneticisine başvurun. Kaynak: <span class="mono">' + src + "</span></p></div></div>" +
      '<div class="card"><div class="card-head"><h3>Şifre değiştir</h3></div><form class="card-pad stack-lg" onsubmit="event.preventDefault();UI.toast(\'Şifre güncellendi. Diğer oturumlar kapatıldı.\')">' + U.field({ name: "cur", label: "Mevcut şifre", type: "password", req: true }) + U.field({ name: "n1", label: "Yeni şifre", type: "password", req: true, help: "En az 8 karakter" }) + U.field({ name: "n2", label: "Yeni şifre tekrar", type: "password", req: true }) + '<button class="btn btn-primary">Şifreyi güncelle</button><p class="xs muted">POST /api/auth/change-password — başarılı olunca diğer cihazlardaki oturumlar kapanır.</p></form></div></div>' +
      '<div class="card card-pad between"><div><b>Oturumu kapat</b><div class="small muted">Bu cihazdaki oturum sonlandırılır.</div></div><a class="btn btn-danger-outline" href="#/public/giris">' + ic("logout", 16) + "Çıkış yap</a></div>";
    return { title: ctx.role === "student" ? "Bilgilerim" : "Profil", html, narrow: true, noState: false, meta: {} };
  };

  /* ───────── Sefer ortak bileşenleri ───────── */
  const T = window.TRIPUI = {};
  T.card = (t, href, o) => {
    o = o || {};
    const tr = D.tripResponse(t), st = L.trip[t.status];
    const delay = D.db.incidents.filter(i => i.tripId === t.id && i.type === "DELAY").slice(-1)[0];
    const r = D.roster(t.id), done = r.filter(x => x.status !== "NONE").length;
    const notices = r.filter(x => x.absenceNotice).length;
    return '<a class="card trip-card' + (t.status === "IN_PROGRESS" ? " active" : "") + '" href="' + href + '">' +
      '<div class="between"><span class="row"><span class="time">' + esc(t.plannedStartTime || "--:--") + '</span><span class="badge b-muted">' + ic(L.dir[t.direction].i, 14) + L.dir[t.direction].s + "</span></span>" + badge(st) + "</div>" +
      '<div class="route">' + esc(tr.routeName) + "</div>" +
      '<div class="crew"><span class="row" style="gap:6px">' + U.plate(tr.vehiclePlate) + "</span><span>" + ic("driver", 14) + " " + esc(tr.driverFullName) + "</span>" + (tr.hostessFullName ? "<span>" + ic("hostess", 14) + " " + esc(tr.hostessFullName) + "</span>" : '<span class="muted">Hostes yok</span>') + "</div>" +
      '<div class="row">' + (delay ? badge(L.incident.DELAY, "+" + delay.delayMinutes + " dk") : "") + (tr.openCancellationRequestId ? badge({ t: "İptal talebi var", c: "b-danger", i: "ban" }) : "") + (notices ? '<span class="badge b-notice">' + ic("hand", 14) + notices + " veli bildirimi</span>" : "") +
      (o.showProgress !== false && t.status !== "CANCELLED" ? '<span class="xs muted" style="margin-left:auto">Yoklama ' + done + "/" + r.length + "</span>" : "") + "</div>" +
      (t.status === "CANCELLED" && t.cancellationReason ? '<div class="small muted">Gerekçe: ' + esc(t.cancellationReason) + "</div>" : "") + "</a>";
  };

  /** Yolcu listesi — durak gruplu. mode: "field" (büyük butonlar) | "admin" (kompakt) */
  T.roster = (tripId, mode) => {
    const t = D.trip(tripId), rows = D.roster(tripId);
    const canAct = t.status === "IN_PROGRESS";
    const groups = [];
    rows.forEach(r => { let g = groups.find(x => x.stopId === r.stopId); if (!g) groups.push(g = { stopId: r.stopId, stopName: r.stopName, seq: r.sequenceNo, time: r.stopPlannedTime, rows: [] }); g.rows.push(r); });
    const nextStop = groups.find(g => g.rows.some(r => r.status === "NONE" && !r.absenceNotice));
    if (!rows.length) return U.stateBlock("empty", { emptyIcon: "users", emptyTitle: "Bu sefere atanmış öğrenci yok", emptyText: "Güzergâh ve yön için aktif öğrenci ataması bulunamadı." });
    const cls = s => ({ NONE: "st-none", BOARDED: "st-boarded", DROPPED_OFF: "st-dropped", ABSENT: "st-absent" }[s]);
    return groups.map(g => '<div class="stop-group"><div class="stop-head' + (nextStop && nextStop.stopId === g.stopId && canAct ? " next" : "") + '"><span class="seq">' + g.seq + '</span><b>' + esc(g.stopName) + "</b>" + (nextStop && nextStop.stopId === g.stopId && canAct ? '<span class="badge b-warn">Sıradaki durak</span>' : "") + '<span class="tm">' + esc(g.time) + "</span></div>" +
      g.rows.map(r => {
        const acts = [];
        if (canAct && r.status === "NONE") { acts.push('<button class="btn btn-primary" data-act="att" data-type="BOARDING" data-trip="' + tripId + '" data-st="' + r.studentId + '">' + ic("board", 18) + "Bindi</button>"); acts.push('<button class="btn btn-danger-outline" data-act="att-absent" data-trip="' + tripId + '" data-st="' + r.studentId + '">' + ic("absent", 18) + "Gelmedi</button>"); }
        if (canAct && r.status === "BOARDED") acts.push('<button class="btn btn-secondary" data-act="att" data-type="DROPOFF" data-trip="' + tripId + '" data-st="' + r.studentId + '">' + ic("drop", 18) + "İndi</button>");
        const corr = mode !== "admin" && r.status !== "NONE" && !r.pendingCorrectionId ? '<button class="btn btn-ghost" data-act="corr-new" data-trip="' + tripId + '" data-st="' + r.studentId + '">' + ic("history", 16) + "Düzeltme iste</button>" : "";
        return '<div class="pax ' + cls(r.status) + (r.absenceNotice ? " has-notice" : "") + '"><div class="who"><span class="avatar">' + U.initials(r.studentFullName) + '</span><div class="grow" style="flex:1"><b>' + esc(r.studentFullName) + '</b><div class="row" style="margin-top:4px">' + badge(L.roster[r.status]) + (r.absenceNotice ? U.noticeBadge() : "") + (r.pendingCorrectionId ? badge({ t: "Düzeltme bekliyor", c: "b-warn", i: "history" }) : "") + "</div></div></div>" +
          (r.absenceNotice ? '<div class="note" style="background:var(--surface);border:1px solid var(--notice-line)"><b style="color:var(--notice)">' + esc(r.absenceNotice.parentFullName) + " · " + U.time(r.absenceNotice.createdAt) + "</b> — " + esc(r.absenceNotice.note || "Not yok") + '<div class="xs muted">Resmi yoklama değildir. Öğrenci durağa gelirse “Bindi” kaydedilebilir.</div></div>' : "") +
          (acts.length || corr ? '<div class="acts" style="grid-template-columns:repeat(' + Math.max(1, acts.length + (corr ? 1 : 0)) + ',1fr)">' + acts.join("") + corr + "</div>" : "") + "</div>";
      }).join("") + "</div>").join("");
  };
  A.att = el => { const p = APP.persona(); D.api.recordAttendance(+el.dataset.trip, +el.dataset.st, el.dataset.type, null, p.userId); U.toast((el.dataset.type === "BOARDING" ? "Biniş" : "İniş") + " kaydedildi: " + D.student(+el.dataset.st).fullName); APP.render(); };
  A["att-absent"] = el => {
    const tripId = +el.dataset.trip, st = +el.dataset.st, r = D.roster(tripId).find(x => x.studentId === st);
    U.dialog({ title: "Gelmedi olarak işaretle", icon: "absent", tone: "danger", sub: esc(r.studentFullName) + " · " + esc(r.stopName), body: (r.absenceNotice ? '<div class="alert a-notice">' + ic("hand", 18) + "<div><b>Veli bildirimi var</b>" + esc(r.absenceNotice.note || "") + "</div></div>" : "") + U.field({ name: "reason", label: "Not (isteğe bağlı)", type: "textarea", placeholder: "Örn. Durakta 2 dk beklendi" }), ok: "Gelmedi kaydet", okClass: "btn-danger",
      onSubmit: d => { D.api.recordAttendance(tripId, st, "ABSENCE", d.reason, APP.persona().userId); U.toast("Devamsızlık kaydedildi"); } });
  };
  A["corr-new"] = el => {
    const tripId = +el.dataset.trip, st = +el.dataset.st, cur = D.rosterStatus(tripId, st);
    const opts = ["NONE", "BOARDED", "DROPPED_OFF", "ABSENT"].filter(x => x !== cur).map(x => [x, L.roster[x].t]);
    U.dialog({ title: "Yoklama düzeltme talebi", icon: "history", tone: "warn", sub: esc(D.student(st).fullName) + " · mevcut kayıt: <b>" + L.roster[cur].t + "</b>",
      body: '<div class="alert a-info">' + ic("info", 18) + "<div>Talep yöneticiye gider. Kayıt, onaylanana kadar değişmez.</div></div>" + U.field({ name: "requestedStatus", label: "Olması gereken durum", type: "select", options: opts, req: true }) + U.field({ name: "reason", label: "Gerekçe", type: "textarea", req: true, placeholder: "Ne oldu? Örn. öğrenci ikinci durakta bindi" }),
      ok: "Talebi gönder", okIcon: "send",
      onSubmit: d => { if (!d.reason.trim()) return "Gerekçe zorunludur."; D.api.createCorrection({ tripId, studentId: st, requestedStatus: d.requestedStatus, reason: d.reason }, APP.persona().userId, APP.persona().role); U.toast("Düzeltme talebi gönderildi"); } });
  };

  T.incidents = (tripId, o) => {
    o = o || {};
    const list = D.db.incidents.filter(i => i.tripId === tripId).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    if (!list.length) return '<div class="state" style="padding:28px"><div class="ill">' + ic("checkCircle", 26) + "</div><b>Kayıt yok</b><p>Bu sefer için gecikme, arıza veya talep bildirilmedi.</p></div>";
    return '<div class="list">' + list.map(i => { const by = D.user(i.reportedByUserId); return '<div class="li" style="align-items:flex-start"><span class="avatar" style="background:var(--surface-sunken);color:var(--ink-2)">' + ic(L.incident[i.type].i, 18) + '</span><div class="grow"><div class="row">' + badge(L.incident[i.type], i.delayMinutes ? "+" + i.delayMinutes + " dk" : "") + badge(L.incStatus[i.status]) + '</div><div style="margin-top:6px">' + esc(i.note) + '</div><div class="meta">' + esc(by.fullName) + " (" + L.role[by.role] + ") · " + U.rel(i.createdAt) + "</div>" +
      (i.resolutionNote ? '<div class="small" style="margin-top:6px"><b>Karar:</b> ' + esc(i.resolutionNote) + " · " + U.rel(i.resolvedAt) + "</div>" : "") +
      (o.admin && i.status === "OPEN" ? '<div class="row" style="margin-top:10px"><button class="btn btn-danger btn-sm" data-act="inc-resolve" data-id="' + i.id + '" data-ok="1">' + ic("ban", 15) + "Onayla ve seferi iptal et</button><button class=\"btn btn-secondary btn-sm\" data-act=\"inc-resolve\" data-id=\"" + i.id + "\" data-ok=\"0\">" + ic("x", 15) + "Reddet</button></div>" : "") + "</div></div>"; }).join("") + "</div>";
  };
  A["inc-resolve"] = el => {
    const inc = D.by(D.db.incidents, +el.dataset.id), ok = el.dataset.ok === "1", t = D.tripResponse(D.trip(inc.tripId));
    U.dialog({ title: ok ? "İptal talebini onayla" : "İptal talebini reddet", icon: ok ? "ban" : "x", tone: ok ? "danger" : "warn", sub: esc(t.routeName) + " · " + esc(t.plannedStartTime),
      body: '<div class="alert a-muted">' + ic("info", 18) + "<div><b>Talep (" + esc(D.user(inc.reportedByUserId).fullName) + ")</b>" + esc(inc.note) + "</div></div>" + (ok ? '<div class="alert a-danger">' + ic("alert", 18) + "<div><b>Sefer iptal edilecek</b>Yolcu listesindeki " + D.roster(t.id).length + " öğrenci, velileri ve personel bilgilendirilir. Bu işlem geri alınamaz.</div></div>" : "") + U.field({ name: "note", label: ok ? "İptal gerekçesi (velilere gönderilir)" : "Ret gerekçesi (talep sahibine gönderilir)", type: "textarea", req: true }),
      ok: ok ? "Onayla, seferi iptal et" : "Talebi reddet", okClass: ok ? "btn-danger" : "btn-primary",
      onSubmit: d => { if (!d.note.trim()) return "Gerekçe zorunludur."; D.api.resolveIncident(inc.id, ok, d.note, APP.persona().userId); U.toast(ok ? "Sefer iptal edildi, bildirimler gönderildi" : "Talep reddedildi"); } });
  };

  /** Saha personeli: olay bildir / iptal talebi */
  A["inc-new"] = el => {
    const tripId = +el.dataset.trip, cancel = el.dataset.type === "CANCELLATION_REQUEST";
    if (cancel) return U.dialog({ title: "İptal talebi gönder", icon: "ban", tone: "danger", sub: "Seferi siz iptal edemezsiniz; talep yöneticiye gider.",
      body: '<div class="alert a-warn">' + ic("alert", 18) + "<div><b>Sefer durumu değişmez</b>Yönetici onaylayana kadar sefer planlandığı gibi görünür. Aynı sefer için tek açık talep olabilir.</div></div>" + U.field({ name: "note", label: "Neden iptal edilmeli?", type: "textarea", req: true, placeholder: "Örn. araçta arıza, yedek araç yok" }),
      ok: "Talebi gönder", okClass: "btn-danger", okIcon: "send", onSubmit: d => { if (!d.note.trim()) return "Açıklama zorunludur."; D.api.reportIncident(tripId, "CANCELLATION_REQUEST", null, d.note, APP.persona().userId); U.toast("İptal talebi yöneticiye iletildi"); } });
    U.dialog({ title: "Olay bildir", icon: "alert", tone: "warn", sub: "Gecikme velilere ve öğrencilere, arıza/yol kapanması yöneticiye bildirilir.",
      body: '<div class="field"><label>Olay türü <span class="req">*</span></label><div class="grid g-3" style="gap:8px">' + ["DELAY", "BREAKDOWN", "ROAD_CLOSURE"].map((k, i) => '<label class="card card-pad row" style="padding:12px;cursor:pointer"><input type="radio" name="type" value="' + k + '"' + (i === 0 ? " checked" : "") + ' style="accent-color:var(--brand)"> ' + ic(L.incident[k].i, 18) + " " + L.incident[k].t + "</label>").join("") + "</div></div>" +
        U.field({ name: "delayMinutes", label: "Tahmini gecikme (dk)", type: "number", placeholder: "1–600", help: "Sadece gecikmede zorunlu" }) + U.field({ name: "note", label: "Açıklama", type: "textarea", req: true, placeholder: "Kısa ve net: nerede, ne oldu?" }),
      ok: "Bildir", okIcon: "send", onSubmit: d => { if (!d.note.trim()) return "Açıklama zorunludur."; if (d.type === "DELAY" && !(+d.delayMinutes >= 1 && +d.delayMinutes <= 600)) return "Gecikme için 1–600 dk arası süre girin."; D.api.reportIncident(tripId, d.type, +d.delayMinutes || null, d.note, APP.persona().userId); U.toast("Olay kaydedildi"); } });
  };
  A["trip-start"] = el => { U.dialog({ title: "Seferi başlat", icon: "play", tone: "ok", body: "<p>Sefer “Yolda” durumuna geçer; veliler ve öğrenciler canlı konumu görmeye başlar.</p>", ok: "Başlat", okIcon: "play", onSubmit: () => { D.api.startTrip(+el.dataset.trip); U.toast("Sefer başladı"); } }); };
  A["trip-complete"] = el => {
    const r = D.roster(+el.dataset.trip), open = r.filter(x => x.status === "NONE" || x.status === "BOARDED");
    U.dialog({ title: "Seferi tamamla", icon: "flag", tone: "info", body: (open.length ? '<div class="alert a-warn">' + ic("alert", 18) + "<div><b>" + open.length + " öğrencinin yoklaması tamamlanmadı</b>" + open.map(x => esc(x.studentFullName) + " (" + L.roster[x.status].t + ")").join(", ") + "</div></div>" : '<div class="alert a-ok">' + ic("checkCircle", 18) + "<div>Tüm yoklamalar tamam.</div></div>") + "<p class='small muted'>Tamamlandıktan sonra yoklama değişikliği yalnızca düzeltme talebiyle yapılır.</p>", ok: "Tamamla", okIcon: "flag", onSubmit: () => { D.api.completeTrip(+el.dataset.trip); U.toast("Sefer tamamlandı"); } });
  };
  T.stopsTimeline = (routeId, o) => {
    o = o || {};
    const stops = D.db.stops.filter(s => s.routeId === routeId).sort((a, b) => a.sequenceNo - b.sequenceNo);
    return '<ol class="stops">' + stops.map(s => '<li class="' + (o.doneUntil != null && s.sequenceNo <= o.doneUntil ? "done" : "") + (o.current === s.sequenceNo ? " current" : "") + (o.mine === s.id ? " mine" : "") + '"><span class="tm">' + esc(s.plannedTime) + '</span><span class="pin"></span><span class="nm">' + esc(s.name) + (o.mine === s.id ? ' <span class="badge b-notice" style="margin-left:6px">' + ic("pin", 12) + (o.mineLabel || "Durağınız") + "</span>" : "") + '</span><span class="xs muted">' + (o.counts ? (o.counts[s.id] || 0) + " öğrenci" : "") + "</span></li>").join("") + "</ol>";
  };

  /* ───────── Ekran dizini ───────── */
  window.renderIndex = function () {
    const sec = (title, role, items) => '<section class="stack"><div class="section-title"><h2>' + title + '</h2></div><div class="index-grid">' + items.map(([h, t, s]) => '<a href="#/' + role + "/" + h + '"><b>' + t + "</b><span>" + s + "</span></a>").join("") + "</div></section>";
    return '<div class="index-page"><div class="row"><span class="brand-mark">' + ic("bus", 20) + '</span><h1>ServisFlow · Sprint 1 ekran prototipi</h1></div>' +
      '<div class="alert a-info">' + ic("info", 18) + '<div><b>Bu bir tasarım prototipidir.</b> Veriler sentetiktir ve yalnızca bu tarayıcı sekmesinde tutulur. Üstteki araç çubuğuyla rol, ekran genişliği (1440 / 390 / 360) ve ekran durumu (yükleniyor, boş, hata, yetki yok) değiştirilebilir. “Örnek veriyi sıfırla” akışları baştan başlatır.</div></div>' +
      '<div class="grid g-2"><section class="stack"><h2 style="font-size:17px">Uçtan uca akış 1 — Veli bildirimi</h2><div class="flow"><a href="#/parent/servisim">Veli: Bugünkü Servis → akşam seferi için “Servise binmeyecek” bildir</a><a href="#/hostess/yoklama/104">Hostes: Yolcu listesinde bildirimi gör (resmi yoklamadan ayrı)</a><a href="#/admin/seferler/104?tab=yolcular">Yönetici: Sefer detayı → Yolcular sekmesinde bildirimi gör</a><a href="#/parent/servisim">Veli: Yoklama girilmeden bildirimi geri al</a></div></section>' +
      '<section class="stack"><h2 style="font-size:17px">Uçtan uca akış 2 — Yoklama düzeltme</h2><div class="flow"><a href="#/hostess/yoklama/101">Hostes: Aktif seferde Yusuf Aksoy → “Düzeltme iste” (Gelmedi → Bindi)</a><a href="#/admin/yoklama-duzeltmeleri">Yönetici: Talebi incele, onayla veya gerekçeyle reddet</a><a href="#/hostess/duzeltme-taleplerim">Hostes: Talep sonucunu ve bildirimi gör</a><a href="#/admin/seferler/101?tab=yolcular">Yönetici: Sefer yolcu listesinde güncel durum</a></div></section></div>' +
      sec("Ortak ve herkese açık", "public", [["giris", "Giriş", "Hata durumu dahil"], ["kayit-talebi", "Kayıt talebi", "Public, 202"], ["e-posta-dogrula", "E-posta doğrulama", "Başarılı / geçersiz"], ["sifremi-unuttum", "Şifremi unuttum", ""], ["sifre-sifirla", "Şifre sıfırlama", "Doğrulama hatası"]]) +
      sec("Yönetici (ADMIN)", "admin", [["operasyon", "Günlük Operasyon", "Bugünkü seferler, olaylar, bekleyen işler"], ["seferler", "Seferler", "Liste, filtre, oluştur"], ["seferler/101", "Sefer detayı (yolda)", "Özet, yolcular, olaylar, konum"], ["seferler/105?tab=olaylar", "İptal talebi kararı", "Açık talep"], ["seferler/104", "Planlı sefer", "Düzenle / iptal"], ["konum", "Canlı konum", "Liste + harita"], ["talepler", "Talep merkezi", "Tüm onay kuyrukları"], ["yoklama-duzeltmeleri", "Yoklama düzeltmeleri", "Onay / ret / eski veri"], ["durak-talepleri", "Durak değişiklikleri", ""], ["ogrenci-basvurulari", "Öğrenci başvuruları", "İlk kayıt + ikinci çocuk"], ["kayit-talepleri", "Hesap kayıt talepleri", "Rol ver / reddet"], ["ogrenciler", "Öğrenciler", "Ortak liste şablonu"], ["ogrenciler/1", "Öğrenci detayı", "Veliler, atama, yoklama, finans"], ["veliler", "Veliler", ""], ["soforler", "Şoförler", ""], ["hostesler", "Hostesler", ""], ["araclar", "Araçlar", ""], ["okullar", "Okullar", ""], ["guzergahlar", "Güzergâhlar", ""], ["guzergahlar/1", "Güzergâh detayı", "Durak CRUD"], ["atamalar", "Öğrenci atamaları", ""], ["kullanicilar", "Kullanıcılar", "Rol, aktif/pasif"], ["sozlesmeler", "Sözleşmeler", ""], ["tahakkuklar", "Tahakkuklar", "Tahakkuk çalıştır"], ["odemeler", "Ödemeler", "Kaydet, iptal, makbuz"], ["raporlar", "Raporlar", ""], ["toplu-bildirim", "Toplu bildirim", ""], ["ayarlar", "Kurum ayarları", ""]]) +
      sec("Şoför (DRIVER)", "driver", [["bugun", "Bugün", "Aktif sefer ve sıradaki"], ["yoklama/101", "Aktif sefer · yoklama", "Durak bazlı yolcu listesi"], ["yoklama/101?tab=konum", "Konum paylaşımı", "Gönder + son konum"], ["yoklama/104", "Planlı sefer", "Başlamadı durumu"], ["guzergahim", "Güzergâhım", "Sıralı duraklar"], ["aracim", "Aracım", ""], ["duzeltme-taleplerim", "Düzeltme taleplerim", ""]]) +
      sec("Hostes (HOSTESS)", "hostess", [["bugun", "Bugün", ""], ["yoklama/101", "Aktif sefer · yoklama", "Biniş/iniş/devamsızlık"], ["yoklama/104", "Akşam seferi", "Veli bildirimi"], ["guzergahim", "Güzergâhım", ""], ["duzeltme-taleplerim", "Düzeltme taleplerim", ""]]) +
      sec("Veli (PARENT)", "parent", [["servisim", "Bugünkü Servis", "Çocuk seçimi, gidiş/dönüş, konum"], ["servisim?child=2", "İkinci çocuk", "Konum yok örneği"], ["yoklama-gecmisi", "Yoklama geçmişi", "İnceleme talebi"], ["taleplerim", "Taleplerim", "Durak, yoklama, başvuru"], ["odemeler", "Ödemeler ve ekstre", "Borç, makbuz, ekstre"], ["cocuklarim", "Çocuklarım", "Bilgi ve atama"], ["ogrenci-ekle", "Öğrenci ekle", "İkinci çocuk sihirbazı"], ["onay-bekliyor", "Onay bekliyor", "İlk öz-kayıt sonrası kapı"], ["bildirimler", "Bildirimler", ""]]) +
      sec("Öğrenci (STUDENT)", "student", [["servisim", "Bugünkü Servisim", ""], ["guzergahim", "Güzergâhım", ""], ["yoklama-gecmisi", "Yoklama geçmişim", ""], ["bildirimler", "Bildirimler", ""], ["profil", "Bilgilerim", ""]]) +
      "</div>";
  };
})();
