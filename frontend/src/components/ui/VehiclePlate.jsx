import styles from "./ui.module.scss";

/** TR plaka gösterimi — plaka her zaman bu bileşenle gösterilir. */
export default function VehiclePlate({ plate, inverse = false }) {
  if (!plate) return <span className="muted">Plaka yok</span>;
  return (
    <span className={`${styles.plate} ${inverse ? styles.plateInverse : ""}`} aria-label={`Plaka ${plate}`}>
      <span className={styles.plateTr} aria-hidden="true">
        TR
      </span>
      <span className={styles.plateText}>{plate}</span>
    </span>
  );
}
