import styles from "../style/auth.module.scss";

const ROLES = [
  ["Yönetici", "Günlük operasyon, talepler ve finans tek ekranda."],
  ["Şoför ve hostes", "Bugünkü sefer, yoklama ve konum paylaşımı."],
  ["Veli", "Bugünkü servis, durak saati ve bildirimler."],
  ["Öğrenci", "Servis saatim, güzergâhım ve yoklama geçmişim."],
];

/** Herkese açık sayfa düzeni: solda kimlik paneli (masaüstü), sağda form. */
export default function AuthLayout({ title, lede, children }) {
  return (
    <div className={styles.auth}>
      <aside className={styles.aside} aria-hidden="true">
        <div className={styles.brand}>
          <span className={styles.brandMark}>
            <i className="pi pi-car" />
          </span>
          <span className={styles.brandName}>ServisFlow</span>
        </div>
        <h2 className={styles.asideTitle}>Okul servisinin günü, herkes için tek ekranda.</h2>
        <div className={styles.roles}>
          {ROLES.map(([t, d]) => (
            <div key={t}>
              <b>{t}</b>
              {d}
            </div>
          ))}
        </div>
      </aside>
      <main className={styles.formSide}>
        <div className={styles.panel}>
          <div className={styles.mobileBrand}>
            <span className={styles.brandMark}>
              <i className="pi pi-car" aria-hidden="true" />
            </span>
            <span className={styles.brandNameDark}>ServisFlow</span>
          </div>
          <div>
            <h1 className={styles.title}>{title}</h1>
            {lede && <p className={styles.lede}>{lede}</p>}
          </div>
          {children}
        </div>
      </main>
    </div>
  );
}
