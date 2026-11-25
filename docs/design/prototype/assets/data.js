/* ServisFlow prototip — SENTETİK örnek veri ve sahte API.
 * Tüm isimler, adresler, plakalar ve tutarlar uydurmadır. Alan adları docs/API.md'deki DTO'larla
 * aynı tutuldu (TripResponse, roster satırı, AttendanceCorrectionResponse, DailyServiceEntryResponse...)
 * ki Sprint 2'de ekranlar gerçek yanıtlara birebir bağlanabilsin.
 * Durum sessionStorage'da tutulur (aynı sekmede rol değiştirince akış devam eder). */
(function () {
  const TODAY = "2026-11-18";
  const KEY = "servisflow-proto-v1";

  function seed() {
    const users = [
      { id: 1, fullName: "Selin Aydın", email: "selin.aydin@ornek-servis.test", phone: "0555 010 10 01", role: "ADMIN", isActive: true },
      { id: 2, fullName: "Okan Er", email: "okan.er@ornek-servis.test", phone: "0555 010 10 02", role: "ADMIN", isActive: true },
      { id: 11, fullName: "Murat Kılıç", email: "murat.kilic@ornek-servis.test", phone: "0555 020 20 11", role: "DRIVER", isActive: true },
      { id: 12, fullName: "Hakan Demir", email: "hakan.demir@ornek-servis.test", phone: "0555 020 20 12", role: "DRIVER", isActive: true },
      { id: 13, fullName: "Emre Şahin", email: "emre.sahin@ornek-servis.test", phone: "0555 020 20 13", role: "DRIVER", isActive: true },
      { id: 21, fullName: "Ayşe Yıldız", email: "ayse.yildiz@ornek-servis.test", phone: "0555 030 30 21", role: "HOSTESS", isActive: true },
      { id: 22, fullName: "Zeynep Arslan", email: "zeynep.arslan@ornek-servis.test", phone: "0555 030 30 22", role: "HOSTESS", isActive: true },
      { id: 31, fullName: "Elif Korkmaz", email: "elif.korkmaz@ornek-posta.test", phone: "0532 400 00 31", role: "PARENT", isActive: true },
      { id: 32, fullName: "Burak Öztürk", email: "burak.ozturk@ornek-posta.test", phone: "0532 400 00 32", role: "PARENT", isActive: true },
      { id: 33, fullName: "Gizem Tan", email: "gizem.tan@ornek-posta.test", phone: "0532 400 00 33", role: "PARENT", isActive: true },
      { id: 34, fullName: "Cem Polat", email: "cem.polat@ornek-posta.test", phone: "0532 400 00 34", role: "PARENT", isActive: false },
      { id: 41, fullName: "Deniz Korkmaz", email: "deniz.korkmaz@ornek-okul.test", phone: "", role: "STUDENT", isActive: true },
    ];
    const drivers = [
      { id: 1, userId: 11, licenseNo: "B-482911", isActive: true },
      { id: 2, userId: 12, licenseNo: "D-118204", isActive: true },
      { id: 3, userId: 13, licenseNo: "D-990317", isActive: true },
    ];
    const hostesses = [{ id: 1, userId: 21, isActive: true }, { id: 2, userId: 22, isActive: true }];
    const parents = [
      { id: 1, userId: 31, fullName: "Elif Korkmaz", phone: "0532 400 00 31", tcNo: "•••••••0418" },
      { id: 2, userId: 32, fullName: "Burak Öztürk", phone: "0532 400 00 32", tcNo: "•••••••7712" },
      { id: 3, userId: 33, fullName: "Gizem Tan", phone: "0532 400 00 33", tcNo: null },
      { id: 4, userId: 34, fullName: "Cem Polat", phone: "0532 400 00 34", tcNo: null },
    ];
    const schools = [
      { id: 1, name: "Yeşilvadi İlkokulu", address: "Lale Cd. No:4, Merkez", phone: "0258 111 11 11", isActive: true },
      { id: 2, name: "Atatepe Ortaokulu", address: "Gazi Bulvarı No:120, Merkez", phone: "0258 222 22 22", isActive: true },
      { id: 3, name: "Kuşadası Sk. Anaokulu", address: "Kuşadası Sk. No:9", phone: "0258 333 33 33", isActive: false },
    ];
    const S = (id, fullName, grade, schoolId, extra) => Object.assign({ id, userId: null, fullName, birthDate: "2016-04-12", tcNo: "•••••••" + (1000 + id), schoolId, schoolName: schools.find(s => s.id === schoolId).name, grade, isActive: true, gender: null, healthNote: null, city: "Denizli", district: "Merkezefendi", neighborhood: "Bağlarbaşı", addressLine: "Örnek Sk. No:" + id }, extra || {});
    const students = [
      S(1, "Deniz Korkmaz", "5-B", 1, { userId: 41, healthNote: "Fıstık alerjisi" }),
      S(2, "Ada Korkmaz", "2-A", 1),
      S(3, "Kerem Öztürk", "4-C", 1),
      S(4, "Mira Çelik", "3-A", 1),
      S(5, "Yusuf Aksoy", "5-A", 1),
      S(6, "Ela Doğan", "1-B", 1),
      S(7, "Can Yalçın", "6-A", 2),
      S(8, "Nehir Polat", "7-B", 2),
      S(9, "Ali Kaya", "6-C", 2),
      S(10, "Ece Öztürk", "1-A", 1, { isActive: true }),
      S(11, "Arda Tan", "3-B", 1),
    ];
    const links = [
      { id: 1, parentId: 1, studentId: 1, relationType: "ANNE", revokedAt: null },
      { id: 2, parentId: 1, studentId: 2, relationType: "ANNE", revokedAt: null },
      { id: 3, parentId: 2, studentId: 3, relationType: "BABA", revokedAt: null },
      { id: 4, parentId: 2, studentId: 10, relationType: "BABA", revokedAt: null },
      { id: 5, parentId: 3, studentId: 11, relationType: "ANNE", revokedAt: null },
      { id: 6, parentId: 4, studentId: 8, relationType: "BABA", revokedAt: "2026-10-02T09:00:00" },
    ];
    const vehicles = [
      { id: 1, plate: "20 SF 101", capacity: 16, model: "Minibüs 16+1", isActive: true },
      { id: 2, plate: "20 SF 204", capacity: 27, model: "Midibüs 27+1", isActive: true },
      { id: 3, plate: "20 SF 318", capacity: 16, model: "Minibüs 16+1", isActive: true },
      { id: 4, plate: "20 SF 422", capacity: 14, model: "Panelvan 14+1", isActive: false },
    ];
    const routes = [
      { id: 1, name: "R1 Bağlarbaşı → Yeşilvadi", direction: "MORNING", isActive: true },
      { id: 2, name: "R1 Yeşilvadi → Bağlarbaşı", direction: "AFTERNOON", isActive: true },
      { id: 3, name: "R2 Gökpınar → Yeşilvadi", direction: "MORNING", isActive: true },
      { id: 4, name: "R2 Yeşilvadi → Gökpınar", direction: "AFTERNOON", isActive: true },
      { id: 5, name: "R3 Karaçay → Atatepe", direction: "MORNING", isActive: true },
      { id: 6, name: "R3 Atatepe → Karaçay", direction: "AFTERNOON", isActive: true },
    ];
    const stops = [
      { id: 1, routeId: 1, name: "Bağlarbaşı Parkı", sequenceNo: 1, plannedTime: "07:15" },
      { id: 2, routeId: 1, name: "Çınar Sokak", sequenceNo: 2, plannedTime: "07:22" },
      { id: 3, routeId: 1, name: "Pazaryeri Kavşağı", sequenceNo: 3, plannedTime: "07:30" },
      { id: 4, routeId: 1, name: "Yeşilvadi İlkokulu", sequenceNo: 4, plannedTime: "07:50" },
      { id: 5, routeId: 2, name: "Yeşilvadi İlkokulu", sequenceNo: 1, plannedTime: "15:40" },
      { id: 6, routeId: 2, name: "Pazaryeri Kavşağı", sequenceNo: 2, plannedTime: "15:58" },
      { id: 7, routeId: 2, name: "Çınar Sokak", sequenceNo: 3, plannedTime: "16:06" },
      { id: 8, routeId: 2, name: "Bağlarbaşı Parkı", sequenceNo: 4, plannedTime: "16:12" },
      { id: 9, routeId: 3, name: "Gökpınar Meydan", sequenceNo: 1, plannedTime: "07:05" },
      { id: 10, routeId: 3, name: "Fidanlık Durağı", sequenceNo: 2, plannedTime: "07:18" },
      { id: 11, routeId: 3, name: "Yeşilvadi İlkokulu", sequenceNo: 3, plannedTime: "07:45" },
      { id: 12, routeId: 4, name: "Yeşilvadi İlkokulu", sequenceNo: 1, plannedTime: "15:40" },
      { id: 13, routeId: 4, name: "Fidanlık Durağı", sequenceNo: 2, plannedTime: "16:05" },
      { id: 14, routeId: 4, name: "Gökpınar Meydan", sequenceNo: 3, plannedTime: "16:18" },
      { id: 15, routeId: 5, name: "Karaçay Çeşme", sequenceNo: 1, plannedTime: "07:20" },
      { id: 16, routeId: 5, name: "Atatepe Ortaokulu", sequenceNo: 2, plannedTime: "07:55" },
      { id: 17, routeId: 6, name: "Atatepe Ortaokulu", sequenceNo: 1, plannedTime: "16:00" },
      { id: 18, routeId: 6, name: "Karaçay Çeşme", sequenceNo: 2, plannedTime: "16:35" },
    ];
    const A = (id, studentId, routeId, stopId, direction) => ({ id, studentId, routeId, stopId, direction, validFrom: "2026-09-07", validTo: null });
    const assignments = [
      A(1, 1, 1, 1, "MORNING"), A(2, 1, 2, 8, "AFTERNOON"),
      A(3, 3, 1, 1, "MORNING"), A(4, 3, 2, 8, "AFTERNOON"),
      A(5, 4, 1, 2, "MORNING"), A(6, 4, 2, 7, "AFTERNOON"),
      A(7, 5, 1, 3, "MORNING"), A(8, 5, 2, 6, "AFTERNOON"),
      A(9, 6, 1, 3, "MORNING"), A(10, 6, 2, 6, "AFTERNOON"),
      A(11, 2, 3, 10, "MORNING"), A(12, 2, 4, 13, "AFTERNOON"),
      A(13, 7, 5, 15, "MORNING"), A(14, 7, 6, 18, "AFTERNOON"),
      A(15, 9, 5, 15, "MORNING"), A(16, 9, 6, 18, "AFTERNOON"),
      A(17, 11, 3, 9, "MORNING"),
    ];
    const T = (o) => Object.assign({ hostessId: null, startedAt: null, completedAt: null, version: 1, cancellationReason: null, openCancellationRequestId: null, businessDate: TODAY }, o);
    const trips = [
      T({ id: 101, routeId: 1, vehicleId: 1, driverId: 1, hostessId: 1, direction: "MORNING", status: "IN_PROGRESS", plannedStartTime: "07:10", startedAt: TODAY + "T07:12:00" }),
      T({ id: 102, routeId: 3, vehicleId: 2, driverId: 2, hostessId: 2, direction: "MORNING", status: "COMPLETED", plannedStartTime: "07:00", startedAt: TODAY + "T07:01:00", completedAt: TODAY + "T07:48:00" }),
      T({ id: 103, routeId: 5, vehicleId: 3, driverId: 3, direction: "MORNING", status: "IN_PROGRESS", plannedStartTime: "07:15", startedAt: TODAY + "T07:19:00" }),
      T({ id: 104, routeId: 2, vehicleId: 1, driverId: 1, hostessId: 1, direction: "AFTERNOON", status: "PLANNED", plannedStartTime: "15:40" }),
      T({ id: 105, routeId: 4, vehicleId: 2, driverId: 2, hostessId: 2, direction: "AFTERNOON", status: "PLANNED", plannedStartTime: "15:40", openCancellationRequestId: 3 }),
      T({ id: 106, routeId: 6, vehicleId: 3, driverId: 3, direction: "AFTERNOON", status: "CANCELLED", plannedStartTime: "16:00", cancellationReason: "Atatepe Ortaokulu bugün 13:00'te kapanıyor; öğrenciler okul aracıyla bırakılacak.", version: 2 }),
      T({ id: 91, businessDate: "2026-11-17", routeId: 1, vehicleId: 1, driverId: 1, hostessId: 1, direction: "MORNING", status: "COMPLETED", plannedStartTime: "07:10", startedAt: "2026-11-17T07:10:00", completedAt: "2026-11-17T07:52:00" }),
      T({ id: 92, businessDate: "2026-11-17", routeId: 2, vehicleId: 1, driverId: 1, hostessId: 1, direction: "AFTERNOON", status: "COMPLETED", plannedStartTime: "15:40", startedAt: "2026-11-17T15:41:00", completedAt: "2026-11-17T16:20:00" }),
      T({ id: 111, businessDate: "2026-11-19", routeId: 1, vehicleId: 1, driverId: 1, hostessId: 1, direction: "MORNING", status: "PLANNED", plannedStartTime: "07:10" }),
    ];
    const ev = (id, tripId, studentId, eventType, at, by, reason) => ({ id, tripId, studentId, eventType, recordedAt: at, recordedByUserId: by, reason: reason || null, voided: false });
    const events = [
      ev(1, 101, 1, "BOARDING", TODAY + "T07:16:00", 21),
      ev(2, 101, 3, "BOARDING", TODAY + "T07:16:30", 21),
      ev(3, 101, 5, "ABSENCE", TODAY + "T07:31:00", 21, "Durakta yoktu, 2 dk beklendi"),
      ev(4, 102, 2, "BOARDING", TODAY + "T07:19:00", 22),
      ev(5, 102, 2, "DROPOFF", TODAY + "T07:47:00", 22),
      ev(6, 102, 11, "ABSENCE", TODAY + "T07:06:00", 22, "Veli telefonla bildirdi"),
      ev(7, 103, 7, "BOARDING", TODAY + "T07:21:00", 13),
      ev(8, 91, 1, "BOARDING", "2026-11-17T07:15:00", 21), ev(9, 91, 1, "DROPOFF", "2026-11-17T07:51:00", 21),
      ev(10, 92, 1, "BOARDING", "2026-11-17T15:42:00", 21), ev(11, 92, 1, "DROPOFF", "2026-11-17T16:13:00", 21),
    ];
    const notices = [
      { id: 1, tripId: 101, studentId: 4, parentFullName: "Merve Çelik", note: "Ateşi var, bugün okula gitmeyecek.", status: "ACTIVE", createdAt: TODAY + "T06:42:00", withdrawnAt: null },
    ];
    const incidents = [
      { id: 1, tripId: 101, type: "DELAY", status: "RECORDED", delayMinutes: 10, note: "Pazaryeri kavşağında yol çalışması, trafik yoğun.", reportedByUserId: 11, createdAt: TODAY + "T07:24:00" },
      { id: 2, tripId: 103, type: "BREAKDOWN", status: "RECORDED", delayMinutes: null, note: "Konum cihazı yanıt vermiyor, araç yolda; telefonla bilgi verilecek.", reportedByUserId: 13, createdAt: TODAY + "T07:22:00" },
      { id: 3, tripId: 105, type: "CANCELLATION_REQUEST", status: "OPEN", delayMinutes: null, note: "Araçta fren uyarısı yandı, servis bakım için çağrıldı. Akşam seferi yapılamayabilir.", reportedByUserId: 22, createdAt: TODAY + "T08:05:00" },
      { id: 4, tripId: 92, type: "DELAY", status: "RECORDED", delayMinutes: 5, note: "Okul çıkışı geç oldu.", reportedByUserId: 11, createdAt: "2026-11-17T15:45:00" },
    ];
    const corrections = [
      { id: 1, tripId: 102, studentId: 11, kind: "CORRECTION", sourceStatus: "ABSENT", requestedStatus: "DROPPED_OFF", reason: "Öğrenci ikinci durakta bindi; yanlışlıkla devamsız işaretlendi.", status: "PENDING", requestedByUserId: 22, createdAt: TODAY + "T08:10:00" },
      { id: 2, tripId: 102, studentId: 2, kind: "REVIEW", sourceStatus: "DROPPED_OFF", requestedStatus: null, reason: "İniş saati 07:47 görünüyor, okul girişte 07:55 kaydetmiş. Kontrol eder misiniz?", status: "PENDING", requestedByUserId: 31, createdAt: TODAY + "T09:02:00" },
      { id: 3, tripId: 91, studentId: 1, kind: "CORRECTION", sourceStatus: "NONE", requestedStatus: "BOARDED", reason: "Biniş tuşuna basılmamış.", status: "APPROVED", requestedByUserId: 21, createdAt: "2026-11-17T07:20:00", decidedByUserId: 1, decidedAt: "2026-11-17T08:00:00", decisionNote: "Hostes kaydıyla doğrulandı.", appliedStatus: "BOARDED" },
      { id: 4, tripId: 92, studentId: 3, kind: "CORRECTION", sourceStatus: "BOARDED", requestedStatus: "ABSENT", reason: "Yanlış öğrenciye basıldı.", status: "REJECTED", requestedByUserId: 11, createdAt: "2026-11-17T16:30:00", decidedByUserId: 1, decidedAt: "2026-11-17T17:10:00", decisionNote: "Veli öğrencinin bindiğini teyit etti." },
    ];
    const stopChanges = [
      { id: 1, studentId: 1, direction: "AFTERNOON", requestedStopId: 7, effectiveDate: "2026-11-24", reason: "Pazartesiden itibaren anneannesinde kalacak.", status: "PENDING", requestedByUserId: 31, createdAt: "2026-11-17T20:14:00" },
      { id: 2, studentId: 3, direction: "MORNING", requestedStopId: 3, effectiveDate: "2026-11-10", reason: "Taşınma", status: "REJECTED", decisionNote: "Pazaryeri durağında kapasite dolu; Çınar Sokak önerildi.", decidedAt: "2026-11-08T10:00:00", requestedByUserId: 32, createdAt: "2026-11-07T19:00:00" },
    ];
    const contracts = [
      { id: 1, studentId: 1, monthlyFee: 3500, discountAmount: 0, startDate: "2026-09-01", endDate: "2027-06-30", status: "ACTIVE", rejectionReason: null },
      { id: 2, studentId: 2, monthlyFee: 3400, discountAmount: 200, startDate: "2026-09-01", endDate: "2027-06-30", status: "ACTIVE", rejectionReason: null },
      { id: 3, studentId: 3, monthlyFee: 3500, discountAmount: 0, startDate: "2026-09-01", endDate: "2027-06-30", status: "ACTIVE", rejectionReason: null },
      { id: 4, studentId: 10, monthlyFee: 3500, discountAmount: 0, startDate: "2026-12-01", endDate: null, status: "PENDING_APPROVAL", rejectionReason: null, createdAt: TODAY + "T10:12:00", secondChild: true },
      { id: 5, studentId: 11, monthlyFee: 3500, discountAmount: 0, startDate: "2026-12-01", endDate: null, status: "PENDING_APPROVAL", rejectionReason: null, createdAt: "2026-11-17T21:40:00", secondChild: false },
      { id: 6, studentId: 8, monthlyFee: 3500, discountAmount: 0, startDate: "2026-09-01", endDate: null, status: "CANCELLED", rejectionReason: "Adres servis bölgesi dışında." },
    ];
    const charges = [];
    let cid = 1;
    [[1, 1, 3500], [2, 2, 3200], [3, 3, 3500]].forEach(([studentId, contractId, amount]) => {
      ["2026-09", "2026-10", "2026-11"].forEach(period => charges.push({ id: cid++, studentId, contractId, period, amount }));
    });
    const payments = [
      { id: 1, studentId: 1, amount: 3500, method: "BANK_TRANSFER", status: "COMPLETED", note: "Eylül", cancelledAt: null, createdAt: "2026-09-05T10:00:00" },
      { id: 2, studentId: 1, amount: 3500, method: "CARD", status: "COMPLETED", note: "Ekim", cancelledAt: null, createdAt: "2026-10-06T14:20:00" },
      { id: 3, studentId: 2, amount: 3200, method: "BANK_TRANSFER", status: "COMPLETED", note: null, cancelledAt: null, createdAt: "2026-09-05T10:05:00" },
      { id: 4, studentId: 2, amount: 3200, method: "CASH", status: "CANCELLED", note: "Mükerrer giriş", cancelledAt: "2026-10-07T09:00:00", createdAt: "2026-10-06T14:25:00" },
      { id: 5, studentId: 2, amount: 3200, method: "CASH", status: "COMPLETED", note: "Ekim", cancelledAt: null, createdAt: "2026-10-07T09:02:00" },
      { id: 6, studentId: 3, amount: 7000, method: "BANK_TRANSFER", status: "COMPLETED", note: "Eylül+Ekim", cancelledAt: null, createdAt: "2026-10-01T11:00:00" },
    ];
    const regRequests = [
      { id: 1, firstName: "Tolga", lastName: "Usta", email: "tolga.usta@ornek-posta.test", status: "EMAIL_VERIFIED", emailVerifiedAt: "2026-11-17T18:02:00", createdAt: "2026-11-17T17:58:00" },
      { id: 2, firstName: "Seda", lastName: "Güneş", email: "seda.gunes@ornek-posta.test", status: "EMAIL_VERIFIED", emailVerifiedAt: TODAY + "T08:31:00", createdAt: TODAY + "T08:29:00" },
      { id: 3, firstName: "Barış", lastName: "Kurt", email: "baris.kurt@ornek-posta.test", status: "PENDING_EMAIL_VERIFICATION", emailVerifiedAt: null, createdAt: TODAY + "T09:40:00" },
      { id: 4, firstName: "Nil", lastName: "Akın", email: "nil.akin@ornek-posta.test", status: "REJECTED", rejectionReason: "Kurumla ilişkisi doğrulanamadı.", rejectedAt: "2026-11-12T10:00:00", createdAt: "2026-11-11T20:00:00" },
    ];
    const notifications = [
      { id: 1, recipientUserId: 31, title: "Durak değişikliği talebiniz alındı", body: "Deniz Korkmaz için akşam durağı değişikliği talebi yönetici onayına gönderildi.", read: true, createdAt: "2026-11-17T20:14:00" },
      { id: 2, recipientUserId: 31, title: "Sabah servisi gecikmeli", body: "R1 Bağlarbaşı → Yeşilvadi: yaklaşık 10 dk gecikme. Not: Pazaryeri kavşağında yol çalışması.", read: false, createdAt: TODAY + "T07:24:00" },
      { id: 3, recipientUserId: 41, title: "Sabah servisi gecikmeli", body: "R1 Bağlarbaşı → Yeşilvadi: yaklaşık 10 dk gecikme.", read: false, createdAt: TODAY + "T07:24:00" },
      { id: 4, recipientUserId: 1, title: "Yeni iptal talebi", body: "R2 Yeşilvadi → Gökpınar (15:40) için Zeynep Arslan iptal talebi açtı.", read: false, createdAt: TODAY + "T08:05:00" },
      { id: 5, recipientUserId: 1, title: "Arıza kaydı", body: "R3 Karaçay → Atatepe: konum cihazı yanıt vermiyor (Emre Şahin).", read: true, createdAt: TODAY + "T07:22:00" },
      { id: 6, recipientUserId: 11, title: "Sefer ataması değişti", body: "19.11.2026 sabah seferi size atandı.", read: true, createdAt: "2026-11-17T12:00:00" },
      { id: 7, recipientUserId: 21, title: "Düzeltme talebiniz onaylandı", body: "17.11.2026 sabah — Deniz Korkmaz: Bindi olarak düzeltildi.", read: true, createdAt: "2026-11-17T08:00:00" },
      { id: 8, recipientUserId: 32, title: "Durak değişikliği talebiniz reddedildi", body: "Gerekçe: Pazaryeri durağında kapasite dolu; Çınar Sokak önerildi.", read: true, createdAt: "2026-11-08T10:00:00" },
      { id: 9, recipientUserId: 34, title: "Öğrenci başvurunuz reddedildi", body: "Nehir Polat başvurusu reddedildi. Gerekçe: Adres servis bölgesi dışında.", read: false, createdAt: "2026-11-02T10:00:00" },
      { id: 10, recipientUserId: 31, title: "Ada Korkmaz başvurusu onaylandı", body: "İkinci çocuk başvurunuz onaylandı; sözleşme aktif.", read: true, createdAt: "2026-09-03T11:00:00" },
    ];
    const locations = {
      101: { lat: 37.7801, lng: 29.0912, recordedAt: TODAY + "T07:33:40", staleSeconds: 25, isOffline: false, progress: 0.58 },
      103: null,
      102: { lat: 37.7712, lng: 29.0801, recordedAt: TODAY + "T07:48:00", staleSeconds: 9000, isOffline: true, progress: 1 },
    };
    const settings = { id: 1, name: "Örnek Servis Taşımacılık", address: "Sanayi Mh. 1201 Sk. No:7, Merkezefendi", phone: "0258 000 00 00", reportHeaderNote: "Bu belge bilgilendirme amaçlıdır.", defaultMonthlyFee: 3500 };
    return { users, drivers, hostesses, parents, schools, students, links, vehicles, routes, stops, assignments, trips, events, notices, incidents, corrections, stopChanges, contracts, charges, payments, regRequests, notifications, locations, settings, seq: 1000, nowTime: "07:34" };
  }

  function load() {
    try { const raw = sessionStorage.getItem(KEY); if (raw) return JSON.parse(raw); } catch (e) { /* depolama kapalı olabilir */ }
    return seed();
  }
  const db = load();
  function save() { try { sessionStorage.setItem(KEY, JSON.stringify(db)); } catch (e) { /* yok say */ } }
  function reset() { try { sessionStorage.removeItem(KEY); } catch (e) { /* yok say */ } location.reload(); }
  const nextId = () => ++db.seq;
  const nowIso = () => TODAY + "T" + (db.nowTime || "07:34") + ":00";

  /* ── Yardımcı sorgular ── */
  const by = (arr, id) => arr.find(x => x.id === id);
  const user = id => by(db.users, id);
  const driverUser = driverId => { const d = by(db.drivers, driverId); return d ? user(d.userId) : null; };
  const hostessUser = hostessId => { const h = by(db.hostesses, hostessId); return h ? user(h.userId) : null; };
  const route = id => by(db.routes, id);
  const stop = id => by(db.stops, id);
  const student = id => by(db.students, id);
  const vehicle = id => by(db.vehicles, id);
  const trip = id => by(db.trips, id);

  /** TripResponse ile aynı şekil (docs/API.md §9). */
  function tripResponse(t) {
    if (!t) return null;
    const d = driverUser(t.driverId), h = t.hostessId ? hostessUser(t.hostessId) : null;
    const openReq = db.incidents.find(i => i.tripId === t.id && i.type === "CANCELLATION_REQUEST" && i.status === "OPEN");
    return Object.assign({}, t, {
      routeName: route(t.routeId).name, vehiclePlate: vehicle(t.vehicleId).plate,
      driverFullName: d ? d.fullName : "—", hostessFullName: h ? h.fullName : null,
      openCancellationRequestId: openReq ? openReq.id : null,
    });
  }

  function activeEvents(tripId, studentId) {
    return db.events.filter(e => e.tripId === tripId && e.studentId === studentId && !e.voided);
  }
  function rosterStatus(tripId, studentId) {
    const ev = activeEvents(tripId, studentId);
    if (ev.some(e => e.eventType === "ABSENCE")) return "ABSENT";
    if (ev.some(e => e.eventType === "DROPOFF")) return "DROPPED_OFF";
    if (ev.some(e => e.eventType === "BOARDING")) return "BOARDED";
    return "NONE";
  }
  /** GET /api/trips/{id}/roster satırları (§9 + 2026-11-18 alanları). */
  function roster(tripId) {
    const t = trip(tripId);
    const rows = db.assignments.filter(a => a.routeId === t.routeId && a.direction === t.direction && student(a.studentId).isActive)
      .map(a => {
        const s = stop(a.stopId);
        const n = db.notices.find(x => x.tripId === tripId && x.studentId === a.studentId && x.status === "ACTIVE");
        const pc = db.corrections.find(c => c.tripId === tripId && c.studentId === a.studentId && c.status === "PENDING");
        return {
          studentId: a.studentId, studentFullName: student(a.studentId).fullName, stopId: s.id, stopName: s.name,
          sequenceNo: s.sequenceNo, stopPlannedTime: s.plannedTime, status: rosterStatus(tripId, a.studentId),
          absenceNotice: n ? { id: n.id, note: n.note, createdAt: n.createdAt, parentFullName: n.parentFullName } : null,
          pendingCorrectionId: pc ? pc.id : null,
        };
      });
    return rows.sort((a, b) => a.sequenceNo - b.sequenceNo || a.studentFullName.localeCompare(b.studentFullName, "tr"));
  }
  function parentOfUser(userId) { return db.parents.find(p => p.userId === userId); }
  function childrenOfParent(parentId) {
    return db.links.filter(l => l.parentId === parentId && !l.revokedAt).map(l => student(l.studentId))
      .filter(s => s && db.contracts.some(c => c.studentId === s.id && c.status === "ACTIVE"));
  }
  function pendingChildrenOfParent(parentId) {
    return db.links.filter(l => l.parentId === parentId && !l.revokedAt).map(l => student(l.studentId))
      .filter(s => s && db.contracts.some(c => c.studentId === s.id && c.status === "PENDING_APPROVAL"));
  }
  /** GET /api/students/{id}/daily-service (§26). */
  function dailyService(studentId, role) {
    return db.assignments.filter(a => a.studentId === studentId).map(a => {
      const t = db.trips.find(x => x.routeId === a.routeId && x.direction === a.direction && x.businessDate === TODAY);
      if (!t) return null;
      const s = stop(a.stopId);
      const n = db.notices.find(x => x.tripId === t.id && x.studentId === studentId && x.status === "ACTIVE");
      const att = rosterStatus(t.id, studentId);
      const open = t.status === "PLANNED" || t.status === "IN_PROGRESS";
      const inc = db.incidents.filter(i => i.tripId === t.id && i.type !== "CANCELLATION_REQUEST").sort((x, y) => y.createdAt.localeCompare(x.createdAt))[0];
      return {
        tripId: t.id, direction: t.direction, businessDate: t.businessDate, status: t.status, plannedStartTime: t.plannedStartTime,
        routeName: route(t.routeId).name, vehiclePlate: vehicle(t.vehicleId).plate, stopId: s.id, stopName: s.name, stopPlannedTime: s.plannedTime,
        attendanceStatus: att, absenceNotice: n ? { id: n.id, note: n.note, createdAt: n.createdAt, parentFullName: n.parentFullName } : null,
        canSubmitAbsenceNotice: role === "PARENT" && open && att === "NONE" && !n,
        canWithdrawAbsenceNotice: role === "PARENT" && open && att === "NONE" && !!n,
        latestIncident: inc ? { type: inc.type, delayMinutes: inc.delayMinutes, note: inc.note, createdAt: inc.createdAt } : null,
        cancellationReason: t.cancellationReason,
      };
    }).filter(Boolean).sort((a, b) => (a.direction === "MORNING" ? 0 : 1) - (b.direction === "MORNING" ? 0 : 1));
  }
  function notify(userIds, title, body) {
    [...new Set(userIds)].filter(Boolean).forEach(uid => db.notifications.unshift({ id: nextId(), recipientUserId: uid, title, body, read: false, createdAt: nowIso() }));
  }
  function parentUserIdsOfStudent(studentId) {
    return db.links.filter(l => l.studentId === studentId && !l.revokedAt).map(l => by(db.parents, l.parentId).userId);
  }
  function tripAudience(t) {
    const studentIds = roster(t.id).map(r => r.studentId);
    const ids = [];
    studentIds.forEach(sid => { const s = student(sid); if (s.userId) ids.push(s.userId); ids.push(...parentUserIdsOfStudent(sid)); });
    return ids;
  }
  function crewUserIds(t) { return [by(db.drivers, t.driverId).userId, t.hostessId ? by(db.hostesses, t.hostessId).userId : null]; }
  const adminIds = () => db.users.filter(u => u.role === "ADMIN" && u.isActive).map(u => u.id);
  const label = (window.LABELS || {});
  function tripTitle(t) { return route(t.routeId).name + " (" + t.plannedStartTime + ")"; }

  /* ── Sahte API işlemleri (iş kuralları docs/API.md ile aynı) ── */
  const api = {
    startTrip(id) { const t = trip(id); if (t.status !== "PLANNED") throw new Error("Sefer PLANNED değil (409)."); t.status = "IN_PROGRESS"; t.startedAt = nowIso(); t.version++; save(); },
    completeTrip(id) { const t = trip(id); if (t.status !== "IN_PROGRESS") throw new Error("Sefer başlamamış (409)."); t.status = "COMPLETED"; t.completedAt = nowIso(); t.version++; save(); },
    cancelTrip(id, reason) {
      const t = trip(id); if (t.status !== "PLANNED" && t.status !== "IN_PROGRESS") throw new Error("Bu sefer iptal edilemez (409).");
      t.status = "CANCELLED"; t.cancellationReason = reason || null; t.version++;
      db.notices.filter(n => n.tripId === id && n.status === "ACTIVE").forEach(n => { /* bildirim kayıtları korunur */ });
      notify(tripAudience(t).concat(crewUserIds(t)), "Sefer iptal edildi", tripTitle(t) + " iptal edildi." + (reason ? " Gerekçe: " + reason : ""));
      save();
    },
    updateTrip(id, patch) {
      const t = trip(id); if (t.status !== "PLANNED") throw new Error("Sadece planlı sefer düzenlenebilir (409).");
      const oldTime = t.plannedStartTime;
      Object.assign(t, patch); t.version++;
      if (patch.plannedStartTime && patch.plannedStartTime !== oldTime) notify(tripAudience(t), "Servis saati değişti", tripTitle(t) + ": yeni planlanan saat " + t.plannedStartTime + ".");
      save();
    },
    createTrip(o) { const t = { id: nextId(), businessDate: o.businessDate, routeId: +o.routeId, vehicleId: +o.vehicleId, driverId: +o.driverId, hostessId: o.hostessId ? +o.hostessId : null, direction: route(+o.routeId).direction, status: "PLANNED", plannedStartTime: o.plannedStartTime || null, startedAt: null, completedAt: null, version: 1, cancellationReason: null }; db.trips.push(t); save(); return t; },
    recordAttendance(tripId, studentId, type, reason, byUserId) {
      const st = rosterStatus(tripId, studentId);
      if (type === "DROPOFF" && st !== "BOARDED") throw new Error("İniş için önce biniş kaydı gerekir (409).");
      if (type === "BOARDING" && st !== "NONE") throw new Error("Bu öğrenci için kayıt zaten var (409).");
      if (type === "ABSENCE" && st !== "NONE") throw new Error("Biniş kaydı olan öğrenci devamsız işaretlenemez (409).");
      db.events.push({ id: nextId(), tripId, studentId, eventType: type, recordedAt: nowIso(), recordedByUserId: byUserId, reason: reason || null, voided: false });
      save();
    },
    submitNotice(tripId, studentId, note, parentUserId) {
      const ex = db.notices.find(n => n.tripId === tripId && n.studentId === studentId && n.status === "ACTIVE");
      if (ex) return ex;
      const n = { id: nextId(), tripId, studentId, parentFullName: user(parentUserId).fullName, note: note || null, status: "ACTIVE", createdAt: nowIso(), withdrawnAt: null };
      db.notices.push(n); save(); return n;
    },
    withdrawNotice(noticeId) { const n = by(db.notices, noticeId); if (rosterStatus(n.tripId, n.studentId) !== "NONE") throw new Error("Yoklama girildiği için geri alınamaz (409)."); n.status = "WITHDRAWN"; n.withdrawnAt = nowIso(); save(); },
    reportIncident(tripId, type, delayMinutes, note, byUserId) {
      const t = trip(tripId);
      if (type === "CANCELLATION_REQUEST") { const ex = db.incidents.find(i => i.tripId === tripId && i.type === type && i.status === "OPEN"); if (ex) return ex; }
      const inc = { id: nextId(), tripId, type, status: type === "CANCELLATION_REQUEST" ? "OPEN" : "RECORDED", delayMinutes: delayMinutes || null, note, reportedByUserId: byUserId, createdAt: nowIso() };
      db.incidents.push(inc);
      if (type === "DELAY") notify(tripAudience(t), "Servis gecikmeli", tripTitle(t) + ": yaklaşık " + delayMinutes + " dk gecikme. " + note);
      else notify(adminIds(), type === "CANCELLATION_REQUEST" ? "Yeni iptal talebi" : "Yeni operasyon kaydı", tripTitle(t) + " — " + user(byUserId).fullName + ": " + note);
      save(); return inc;
    },
    resolveIncident(incId, approve, note, adminUserId) {
      const inc = by(db.incidents, incId); if (inc.status !== "OPEN") throw new Error("Talep zaten sonuçlandı (409).");
      inc.status = approve ? "APPROVED" : "REJECTED"; inc.resolutionNote = note; inc.resolvedByUserId = adminUserId; inc.resolvedAt = nowIso();
      if (approve) api.cancelTrip(inc.tripId, note);
      notify([inc.reportedByUserId], "İptal talebiniz " + (approve ? "onaylandı" : "reddedildi"), tripTitle(trip(inc.tripId)) + " — " + note);
      save();
    },
    createCorrection(o, byUserId, role) {
      if (db.corrections.some(c => c.tripId === o.tripId && c.studentId === o.studentId && c.status === "PENDING")) throw new Error("Bu öğrenci için bekleyen talep var (409).");
      const c = { id: nextId(), tripId: o.tripId, studentId: o.studentId, kind: role === "PARENT" ? "REVIEW" : "CORRECTION", sourceStatus: rosterStatus(o.tripId, o.studentId), requestedStatus: o.requestedStatus || null, reason: o.reason, status: "PENDING", requestedByUserId: byUserId, createdAt: nowIso() };
      db.corrections.unshift(c); notify(adminIds(), "Yeni yoklama " + (c.kind === "REVIEW" ? "inceleme" : "düzeltme") + " talebi", student(o.studentId).fullName + " — " + o.reason); save(); return c;
    },
    approveCorrection(id, newStatus, note, adminUserId) {
      const c = by(db.corrections, id); if (c.status !== "PENDING") throw new Error("Talep zaten sonuçlandı (409).");
      const cur = rosterStatus(c.tripId, c.studentId); if (cur !== c.sourceStatus) throw new Error("Kayıt talepten sonra değişmiş (409 — eski veri).");
      const target = newStatus || c.requestedStatus;
      db.events.filter(e => e.tripId === c.tripId && e.studentId === c.studentId && !e.voided).forEach(e => { e.voided = true; e.voidedByCorrectionId = id; });
      const add = t => db.events.push({ id: nextId(), tripId: c.tripId, studentId: c.studentId, eventType: t, recordedAt: nowIso(), recordedByUserId: adminUserId, createdByCorrectionId: id, voided: false });
      if (target === "BOARDED") add("BOARDING");
      if (target === "DROPPED_OFF") { add("BOARDING"); add("DROPOFF"); }
      if (target === "ABSENT") add("ABSENCE");
      Object.assign(c, { status: "APPROVED", decidedByUserId: adminUserId, decidedAt: nowIso(), decisionNote: note || null, appliedStatus: target });
      notify([c.requestedByUserId], "Yoklama talebiniz onaylandı", student(c.studentId).fullName + ": " + (window.LABELS ? window.LABELS.roster[target].t : target) + " olarak güncellendi."); save();
    },
    rejectCorrection(id, reason, adminUserId) {
      const c = by(db.corrections, id); if (c.status !== "PENDING") throw new Error("Talep zaten sonuçlandı (409).");
      Object.assign(c, { status: "REJECTED", decidedByUserId: adminUserId, decidedAt: nowIso(), decisionNote: reason });
      notify([c.requestedByUserId], "Yoklama talebiniz reddedildi", student(c.studentId).fullName + " — Gerekçe: " + reason); save();
    },
    createStopChange(o, byUserId) {
      if (db.stopChanges.some(r => r.studentId === o.studentId && r.direction === o.direction && r.status === "PENDING")) throw new Error("Bu yön için bekleyen talep var (409).");
      const r = Object.assign({ id: nextId(), status: "PENDING", requestedByUserId: byUserId, createdAt: nowIso() }, o);
      db.stopChanges.unshift(r); notify(adminIds(), "Yeni durak değişikliği talebi", student(o.studentId).fullName); save(); return r;
    },
    decideStopChange(id, approve, note) {
      const r = by(db.stopChanges, id); if (r.status !== "PENDING") throw new Error("Talep zaten sonuçlandı (409).");
      r.status = approve ? "APPROVED" : "REJECTED"; r.decisionNote = note; r.decidedAt = nowIso();
      if (approve) { const a = db.assignments.find(x => x.studentId === r.studentId && x.direction === r.direction); if (a) { a.validTo = "2026-11-23"; const st = stop(r.requestedStopId); const na = { id: nextId(), studentId: r.studentId, routeId: st.routeId, stopId: st.id, direction: r.direction, validFrom: r.effectiveDate, validTo: null }; db.assignments.push(na); r.resultingAssignmentId = na.id; } }
      notify(parentUserIdsOfStudent(r.studentId).concat([student(r.studentId).userId]), "Durak değişikliği talebiniz " + (approve ? "onaylandı" : "reddedildi"), student(r.studentId).fullName + (note ? " — " + note : "")); save();
    },
    decideContract(id, approve, payload) {
      const c = by(db.contracts, id); if (c.status !== "PENDING_APPROVAL") throw new Error("Sadece onay bekleyen sözleşme (409).");
      const pids = parentUserIdsOfStudent(c.studentId);
      if (approve) { c.status = "ACTIVE"; Object.assign(c, payload || {}); notify(pids, student(c.studentId).fullName + " başvurusu onaylandı", "Sözleşme aktif. Servis bilgileri atama yapıldığında görünür."); }
      else { c.status = "CANCELLED"; c.rejectionReason = payload.reason || null; student(c.studentId).isActive = false; db.links.filter(l => l.studentId === c.studentId).forEach(l => l.revokedAt = nowIso()); notify(pids, "Öğrenci başvurunuz reddedildi", student(c.studentId).fullName + " başvurusu reddedildi." + (payload.reason ? " Gerekçe: " + payload.reason : "")); }
      save();
    },
    enroll(o, parentUserId) {
      const p = parentOfUser(parentUserId);
      if (pendingChildrenOfParent(p.id).length) throw new Error("Bekleyen başvurunuz var (409).");
      const sid = nextId(); const school = by(db.schools, +o.schoolId);
      db.students.push({ id: sid, userId: null, fullName: o.fullName, birthDate: o.birthDate, tcNo: "•••••••" + String(sid).slice(-4), schoolId: school.id, schoolName: school.name, grade: o.grade, isActive: true, gender: null, healthNote: o.healthNote || null, city: "Denizli", district: o.district, neighborhood: o.neighborhood, addressLine: o.addressLine });
      db.links.push({ id: nextId(), parentId: p.id, studentId: sid, relationType: o.relationType, revokedAt: null });
      db.contracts.push({ id: nextId(), studentId: sid, monthlyFee: db.settings.defaultMonthlyFee, discountAmount: 0, startDate: "2026-12-01", endDate: null, status: "PENDING_APPROVAL", rejectionReason: null, createdAt: nowIso(), secondChild: childrenOfParent(p.id).length > 0 });
      notify(adminIds(), "Yeni öğrenci başvurusu", o.fullName + " (" + user(parentUserId).fullName + ")"); save();
    },
    markRead(id) { const n = by(db.notifications, id); if (n) n.read = true; save(); },
    markAllRead(userId) { db.notifications.filter(n => n.recipientUserId === userId).forEach(n => n.read = true); save(); },
    broadcast(targetRole, title, body) { const ids = db.users.filter(u => u.isActive && (!targetRole || u.role === targetRole)).map(u => u.id); notify(ids, title, body); save(); return ids.length; },
    sendLocation(tripId) { db.locations[tripId] = { lat: 37.78, lng: 29.09, recordedAt: nowIso(), staleSeconds: 2, isOffline: false, progress: Math.min(0.95, ((db.locations[tripId] || {}).progress || 0.3) + 0.08) }; save(); },
    recordPayment(o) { db.payments.unshift({ id: nextId(), studentId: +o.studentId, amount: +o.amount, method: o.method, status: "COMPLETED", note: o.note || null, cancelledAt: null, createdAt: nowIso() }); save(); },
    cancelPayment(id) { const p = by(db.payments, id); p.status = "CANCELLED"; p.cancelledAt = nowIso(); save(); },
    runBilling(period) { let created = 0, skipped = 0; db.contracts.filter(c => c.status === "ACTIVE").forEach(c => { if (db.charges.some(x => x.studentId === c.studentId && x.period === period)) skipped++; else { db.charges.push({ id: nextId(), studentId: c.studentId, contractId: c.id, period, amount: c.monthlyFee - (c.discountAmount || 0) }); created++; } }); save(); return { created, skipped }; },
    decideRegistration(id, approve, payload) { const r = by(db.regRequests, id); if (approve) { r.status = "APPROVED"; r.approvedAt = nowIso(); db.users.push({ id: nextId(), fullName: r.firstName + " " + r.lastName, email: r.email, phone: payload.phone || "", role: payload.role, isActive: true }); } else { r.status = "REJECTED"; r.rejectionReason = payload.reason; r.rejectedAt = nowIso(); } save(); },
  };

  function statement(studentId) {
    const rows = [];
    db.charges.filter(c => c.studentId === studentId).forEach(c => rows.push({ type: "CHARGE", occurredAt: c.period + "-01T00:00:00", period: c.period, amount: c.amount }));
    db.payments.filter(p => p.studentId === studentId).forEach(p => rows.push({ type: "PAYMENT", occurredAt: p.createdAt, method: p.method, paymentStatus: p.status, amount: p.amount }));
    rows.sort((a, b) => a.occurredAt.localeCompare(b.occurredAt));
    let bal = 0; rows.forEach(r => { if (r.type === "CHARGE") bal += r.amount; else if (r.paymentStatus === "COMPLETED") bal -= r.amount; r.runningBalance = bal; });
    return { studentId, entries: rows, currentBalance: bal };
  }

  window.DB = { db, TODAY, save, reset, api, by, user, driverUser, hostessUser, route, stop, student, vehicle, trip, tripResponse, roster, rosterStatus, dailyService, childrenOfParent, pendingChildrenOfParent, parentOfUser, statement, parentUserIdsOfStudent, adminIds };
})();
