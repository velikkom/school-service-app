import styles from "../style/registration.module.scss";

/**
 * Duyarlı kayıt tablosu: masaüstünde tablo, 900 px altında her satır etiketli karta döner
 * (td `data-label`). columns: [{ key, header, render(row), main? }]; actions(row) son sütun.
 */
export default function RecordTable({ rows, columns, actions, rowKey = (r) => r.id, caption }) {
  return (
    <table className={styles.table}>
      {caption && <caption className="sr-only">{caption}</caption>}
      <thead>
        <tr>
          {columns.map((c) => (
            <th key={c.key} scope="col">
              {c.header}
            </th>
          ))}
          {actions && (
            <th scope="col">
              <span className="sr-only">İşlemler</span>
            </th>
          )}
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={rowKey(row)}>
            {columns.map((c) => (
              <td key={c.key} data-label={c.header} className={c.main ? styles.main : undefined}>
                {c.render(row)}
              </td>
            ))}
            {actions && <td className={styles.actions}>{actions(row)}</td>}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
