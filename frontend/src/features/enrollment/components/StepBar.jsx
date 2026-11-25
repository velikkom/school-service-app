import styles from "../style/enrollment.module.scss";

/** Çubuklu adım göstergesi (DESIGN_SYSTEM.md §6 "Adım göstergesi"). current: 0 tabanlı. */
export default function StepBar({ steps, current }) {
  return (
    <ol className={styles.steps} aria-label="Adımlar">
      {steps.map((label, i) => {
        const state = i < current ? "done" : i === current ? "on" : "todo";
        return (
          <li key={label} className={`${styles.step} ${styles[state]}`} aria-current={state === "on" ? "step" : undefined}>
            <span>
              {i + 1}. {label}
            </span>
            <span className="sr-only">{state === "done" ? " (tamamlandı)" : state === "on" ? " (şu an)" : ""}</span>
          </li>
        );
      })}
    </ol>
  );
}
