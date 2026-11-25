"use client";

import { useState } from "react";
import PageMeta from "@/components/layout/PageMeta";
import ScreenState, { QueryState } from "@/components/feedback/ScreenState";
import Card from "@/components/ui/Card";
import Notice from "@/components/ui/Notice";
import { pageMeta, toList } from "@/lib/http/page";
import { useCorrectionList } from "@/features/attendance-corrections/hooks/useCorrections";
import CorrectionRow from "./CorrectionRow";
import { Pager } from "./CorrectionQueueScreen";
import styles from "@/features/attendance-corrections/style/corrections.module.scss";

/** DRIVER/HOSTESS "Düzeltme Taleplerim": yalnız kendi açtıkları; durum rozeti ve karar notu. */
export default function MyCorrectionRequestsScreen() {
  const [page, setPage] = useState(0);
  const query = useCorrectionList({ page, size: 20 }, { live: true });

  return (
    <>
      <PageMeta title="Düzeltme Taleplerim" />
      <div className={styles.narrow}>
        <Notice tone="info">
          Yeni talep, sefer ekranında öğrenci satırındaki <b>“Düzeltme iste”</b> düğmesiyle açılır. Burada yalnızca kendi açtığınız
          talepler görünür.
        </Notice>
        <Card padded={false}>
          <QueryState
            query={query}
            isEmpty={(d) => toList(d).length === 0}
            empty={
              <ScreenState
                kind="empty"
                icon="pi-history"
                title="Henüz düzeltme talebiniz yok"
                message="Yanlış girilen yoklamalar için sefer ekranından talep açın."
                bare
              />
            }
          >
            {(data) => (
              <div className={styles.list}>
                {toList(data).map((c) => (
                  <CorrectionRow key={c.id} correction={c} />
                ))}
              </div>
            )}
          </QueryState>
          <Pager meta={query.data ? pageMeta(query.data) : null} page={page} setPage={setPage} busy={query.isFetching} />
        </Card>
      </div>
    </>
  );
}
