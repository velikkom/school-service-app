/* DRIVER ve HOSTESS ekranları — mobil öncelikli saha arayüzü.
 * Fark: şoför konum gönderir ve "Aracım"ı görür; hostes konumu sadece okur, plakayı seferden görür. */
(function () {
  const D = window.DB, U = window.UI, T = window.TRIPUI, ic = window.icon, { esc, L, badge } = U;
  const A = window.ACTIONS, db = D.db;
  const mine = (p, date) => db.trips.filter(t => (p.driverId ? t.driverId === p.driverId : t.hostessId === p.hostessId) && (!date || t.businessDate === date)).sort((a, b) => (a.businessDate + a.plannedStartTime).localeCompare(b.businessDate + b.plannedStartTime));

  function activeStrip(t, role) {
    const tr = D.tripResponse(t), r = D.roster(t.id), n = s => r.filter(x => x.status === s).length;
    return '<div class="active-strip"><div class="between"><span class="k">' + (t.status === "IN_PROGRESS" ? "Aktif sefer · başladı " + U.time(t.startedAt) : "Sıradaki sefer") + "</span>" + U.plate(tr.vehiclePlate) + '</div><div class="big">' + esc(tr.routeName) + '</div><div class="row small" style="color:#b9c5cf">' + ic(L.dir[t.direction].i, 15) + L.dir[t.direction].t + " · planlanan " + esc(t.plannedStartTime) + "</div>" +
      '<div class="stats"><div><b>' + n("BOARDED") + "</b><span>Bindi</span></div><div><b>" + n("DROPPED_OFF") + "</b><span>İndi</span></div><div><b>" + n("ABSENT") + "</b><span>Gelmedi</span></div><div><b>" + n("NONE") + '</b><span>Bekleyen</span></div></div><a class="btn btn-lg btn-block" style="background:var(--bus);color:var(--night)" href="#/' + role + "/yoklama/" + t.id + '">' + ic("checkCircle", 20) + (t.status === "IN_PROGRESS" ? "Yoklamaya devam et" : "Sefer ekranını aç") + "</a></div>";
  }

  const bugun = ctx => {
    const p = APP.persona(), todays = mine(p, D.TODAY), act = todays.find(t => t.status === "IN_PROGRESS") || todays.find(t => t.status === "PLANNED");
    const tomorrow = mine(p, "2026-11-19");
    const html = (act ? activeStrip(act, ctx.role) : '<div class="card"><div class="state"><div class="ill">' + ic("moon", 30) + "</div><h3>Bugün başka seferiniz yok</h3><p>Yeni atama yapıldığında bildirim alırsınız.</p></div></div>") +
      '<div class="section-title"><h2>Bugünkü seferlerim</h2><span class="right xs muted">GET /api/trips/mine?businessDate=</span></div><div class="stack">' + todays.map(t => T.card(t, "#/" + ctx.role + "/yoklama/" + t.id)).join("") + "</div>" +
      '<div class="section-title"><h2>Yarın</h2></div><div class="stack">' + (tomorrow.length ? tomorrow.map(t => T.card(t, "#/" + ctx.role + "/yoklama/" + t.id, { showProgress: false })).join("") : '<p class="muted small">Planlı sefer yok.</p>') + "</div>";
    return { title: "Bugün", mTitle: "Merhaba, " + D.user(p.userId).fullName.split(" ")[0], html, narrow: true, meta: { emptyIcon: "bus", emptyTitle: "Bugün atanmış seferiniz yok", emptyText: "Seferler yönetici tarafından atanır. Değişiklikte bildirim alırsınız." } };
  };

  const yoklama = ctx => {
    const p = APP.persona();
    if (!ctx.id) { const t = mine(p, D.TODAY).find(x => x.status === "IN_PROGRESS") || mine(p, D.TODAY).find(x => x.status === "PLANNED"); if (t) { ctx.id = String(t.id); } else return { title: "Yoklama", html: U.stateBlock("empty", { emptyIcon: "checkCircle", emptyTitle: "Aktif sefer yok", emptyText: "Yoklama, başlatılmış seferde girilir." }) }; }
    const t = D.trip(+ctx.id);
    if (!t || (p.driverId ? t.driverId !== p.driverId : t.hostessId !== p.hostessId)) return { title: "Sefer", html: U.stateBlock("forbidden"), noState: true };
    const tr = D.tripResponse(t), tab = ctx.q.tab || "yoklama", isDriver = ctx.role === "driver";
    const r = D.roster(t.id), notices = r.filter(x => x.absenceNotice).length;
    const open = t.status === "PLANNED" || t.status === "IN_PROGRESS";
    const tabs = [["yoklama", "Yoklama", r.length], ["duraklar", "Duraklar"], ["konum", "Konum"], ["olaylar", "Olaylar"]];
    const head = '<div class="card card-pad stack"><div class="between"><div class="row">' + badge(L.trip[t.status]) + '<span class="badge b-muted">' + ic(L.dir[t.direction].i, 14) + L.dir[t.direction].s + " " + esc(t.plannedStartTime) + "</span></div>" + U.plate(tr.vehiclePlate) + '</div><b style="font-size:18px">' + esc(tr.routeName) + "</b>" +
      (t.status === "PLANNED" ? '<div class="alert a-info">' + ic("clock", 18) + "<div><b>Sefer başlamadı</b>Yoklama ve konum paylaşımı sefer başlatılınca açılır." + (notices ? " Şimdiden " + notices + " veli bildirimi var." : "") + "</div></div>" : "") +
      (t.status === "CANCELLED" ? '<div class="alert a-danger">' + ic("ban", 18) + "<div><b>Sefer iptal edildi</b>" + esc(t.cancellationReason || "") + "</div></div>" : "") +
      (tr.openCancellationRequestId ? '<div class="alert a-warn">' + ic("clock", 18) + "<div><b>İptal talebiniz yönetici kararını bekliyor</b>Karar verilene kadar sefer planlandığı gibi devam eder.</div></div>" : "") +
      '</div><div class="tabs">' + tabs.map(([k, l, n]) => '<a class="' + (tab === k ? "on" : "") + '" href="#/' + ctx.role + "/yoklama/" + t.id + "?tab=" + k + '">' + l + (n != null ? ' <span class="n">' + n + "</span>" : "") + "</a>").join("") + "</div>";
    let body = "";
    if (tab === "yoklama") body = (notices ? '<div class="alert a-notice">' + ic("hand", 18) + "<div><b>" + notices + " öğrenci için veli “servise binmeyecek” bildirdi</b>Mor işaretli satırlar. Bu bir bilgilendirmedir; resmi yoklama sizin kaydınızdır.</div></div>" : "") + T.roster(t.id, "field");
    if (tab === "duraklar") body = '<div class="card card-pad">' + T.stopsTimeline(t.routeId, { counts: r.reduce((m, x) => (m[x.stopId] = (m[x.stopId] || 0) + 1, m), {}), doneUntil: t.status === "IN_PROGRESS" ? 2 : t.status === "COMPLETED" ? 99 : null, current: t.status === "IN_PROGRESS" ? 3 : null }) + "</div>";
    if (tab === "konum") {
      const l = db.locations[t.id], stops = db.stops.filter(s => s.routeId === t.routeId);
      body = (t.status !== "IN_PROGRESS" ? U.mapPanel({ empty: true, h: 260, emptyIcon: "clock", emptyTitle: "Sefer başlamadı", emptyText: "Konum yalnızca yoldaki seferde paylaşılır." }) : l ? U.mapPanel({ loc: l, stops, h: 260 }) : U.mapPanel({ empty: true, h: 260, emptyTitle: "Henüz konum gönderilmedi", emptyText: isDriver ? "Paylaşımı aşağıdan başlatın." : "Şoför paylaşımı başlattığında görünür." })) +
        (isDriver ? '<div class="card card-pad stack"><div class="between"><div><b>Konum paylaşımı</b><div class="small muted">Sefer boyunca 15 sn\'de bir gönderilir (POST /api/tracking/trips/{id}/locations)</div></div>' + (t.status === "IN_PROGRESS" ? '<span class="badge ' + (l ? "b-live" : "b-muted") + '">' + (l ? '<span class="pulse"></span>Açık' : ic("signalOff", 14) + "Kapalı") + "</span>" : "") + "</div>" + (t.status === "IN_PROGRESS" ? '<button class="btn btn-primary btn-lg btn-block" data-act="loc-send" data-trip="' + t.id + '">' + ic("live", 20) + (l ? "Şimdi konum gönder" : "Paylaşımı başlat") + '</button><div class="alert a-warn">' + ic("alert", 18) + "<div><b>Konum izni gerekli</b>Tarayıcı konum izni reddedilirse paylaşım yapılamaz; ayarlardan izin verin. (İzin reddedildi durumu)</div></div>" : "") + "</div>"
          : '<div class="alert a-muted">' + ic("info", 18) + "<div>Hostes rolü konumu yalnızca görüntüler; konumu şoför cihazı gönderir.</div></div>");
    }
    if (tab === "olaylar") body = '<div class="card">' + T.incidents(t.id) + "</div>";
    const bar = open ? '<div class="action-bar">' + (t.status === "PLANNED" ? '<button class="btn btn-primary btn-lg" style="flex:2" data-act="trip-start" data-trip="' + t.id + '">' + ic("play", 18) + "Seferi başlat</button>" : '<button class="btn btn-primary btn-lg" style="flex:2" data-act="trip-complete" data-trip="' + t.id + '">' + ic("flag", 18) + "Seferi tamamla</button>") +
      '<button class="btn btn-warn-outline btn-lg" data-act="inc-new" data-trip="' + t.id + '" aria-label="Olay bildir">' + ic("alert", 18) + '<span class="hide-m">Olay bildir</span></button><button class="btn btn-danger-outline btn-lg" data-act="inc-new" data-type="CANCELLATION_REQUEST" data-trip="' + t.id + '"' + (tr.openCancellationRequestId ? " disabled" : "") + ' aria-label="İptal talebi">' + ic("ban", 18) + '<span class="hide-m">İptal talebi</span></button></div>' : "";
    return { title: "Sefer · " + esc(tr.routeName), mTitle: tr.routeName, mSub: L.trip[t.status].t + " · " + tr.vehiclePlate, back: "#/" + ctx.role + "/bugun", html: head + body + bar, stateHead: head, narrow: true, meta: { emptyIcon: "users", emptyTitle: "Bu sefere atanmış öğrenci yok", emptyText: "Yöneticiye bildirin." } };
  };
  A["loc-send"] = el => { D.api.sendLocation(+el.dataset.trip); U.toast("Konum gönderildi"); APP.render(); };

  const guzergahim = ctx => {
    const p = APP.persona(), routeIds = [...new Set(mine(p).map(t => t.routeId))];
    const html = '<div class="xs muted">GET /api/routes (kendi seferlerinizin güzergâhları) · GET /api/routes/{id}/stops — salt okunur</div>' + routeIds.map(id => { const r = D.route(id); return '<div class="card"><div class="card-head"><span class="avatar" style="background:var(--brand-soft)">' + ic(L.dir[r.direction].i, 18) + '</span><div><h3>' + esc(r.name) + '</h3><div class="sub">' + L.dir[r.direction].t + " · " + db.stops.filter(s => s.routeId === id).length + ' durak</div></div></div><div class="card-pad">' + T.stopsTimeline(id, { counts: db.assignments.filter(a => a.routeId === id && !a.validTo).reduce((m, a) => (m[a.stopId] = (m[a.stopId] || 0) + 1, m), {}) }) + "</div></div>"; }).join("");
    return { title: "Güzergâhım", html, narrow: true, meta: { emptyIcon: "route", emptyTitle: "Atanmış güzergâh yok", emptyText: "Size sefer atandığında güzergâhı burada görürsünüz." } };
  };
  const aracim = () => {
    const v = D.vehicle(1);
    const html = '<div class="card card-pad stack-lg"><div class="between">' + U.plate(v.plate) + badge(L.active[v.isActive]) + '</div><dl class="dl"><dt>Model</dt><dd>' + esc(v.model) + "</dd><dt>Kapasite</dt><dd>" + v.capacity + " yolcu</dd><dt>Bugünkü seferler</dt><dd>" + db.trips.filter(t => t.vehicleId === v.id && t.businessDate === D.TODAY).length + '</dd></dl><p class="xs muted">GET /api/vehicles/mine — sadece aktif atandığınız araç, salt okunur.</p></div>' +
      '<div class="design-note"><b>Kapsam dışı:</b> Muayene, sigorta, kilometre gibi alanlar VehicleResponse\'ta yok; bu ekranda gösterilmez. ' + U.newApi("Araç belge/bakım bilgisi") + "</div>";
    return { title: "Aracım", html, narrow: true, meta: { emptyIcon: "vehicle", emptyTitle: "Atanmış araç yok", emptyText: "Aktif bir seferde atanmış aracınız bulunmuyor (404)." } };
  };
  const duzeltme = () => {
    const p = APP.persona(), list = db.corrections.filter(c => c.requestedByUserId === p.userId);
    const html = '<div class="alert a-info">' + ic("info", 18) + "<div>Yeni talep, sefer ekranında öğrenci satırındaki <b>“Düzeltme iste”</b> düğmesiyle açılır. Burada yalnızca kendi açtığınız talepler görünür.</div></div>" +
      (list.length ? '<div class="card"><div class="list">' + list.map(c => T.corrRow(c, false)).join("") + "</div></div>" : U.stateBlock("empty", { emptyIcon: "history", emptyTitle: "Henüz talebiniz yok" }));
    return { title: "Düzeltme Taleplerim", html, narrow: true, meta: { emptyIcon: "history", emptyTitle: "Henüz düzeltme talebiniz yok", emptyText: "Yanlış girilen yoklamalar için sefer ekranından talep açın." } };
  };

  ["driver", "hostess"].forEach(role => {
    const S = window.SCREENS[role];
    S.bugun = bugun; S.yoklama = yoklama; S.guzergahim = guzergahim; S["duzeltme-taleplerim"] = duzeltme;
  });
  window.SCREENS.driver.aracim = aracim;
})();
