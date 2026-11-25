"use client";

import { Suspense, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Button } from "primereact/button";
import PageMeta from "@/components/layout/PageMeta";
import ScreenState, { QueryState } from "@/components/feedback/ScreenState";
import Card, { DefinitionList } from "@/components/ui/Card";
import StatusBadge from "@/components/ui/StatusBadge";
import { ActiveBadge } from "@/components/data/ManagementList";
import { useDialogParams } from "@/components/data/useListState";
import { usersService } from "@/features/users/service/usersService";
import { userKeys } from "@/features/users/hooks/keys";
import { RoleChangeDialog, UserFormDialog, useUserActivation } from "./UserManagementScreen";

/** /kullanicilar/[id] (ADMIN) — hesap bilgisi, düzenle, rol değiştir, aktif/pasif. */
export default function UserDetailScreen({ id }) {
  return (
    <Suspense fallback={<ScreenState kind="loading" />}>
      <UserDetail id={id} />
    </Suspense>
  );
}

function UserDetail({ id }) {
  const query = useQuery({ queryKey: [...userKeys.detail(), String(id)], queryFn: () => usersService.get(id) });
  const dlg = useDialogParams();
  const setActive = useUserActivation();
  const [roleOpen, setRoleOpen] = useState(false);
  const user = query.data;

  return (
    <>
      <PageMeta title={user?.fullName ?? "Kullanıcı"} crumb="Yönetim / Kullanıcılar" back="/kullanicilar" />
      <QueryState query={query} forbiddenHome="/kullanicilar">
        {(u) => (
          <Card
            title={u.fullName}
            subtitle={u.email}
            actions={
              <div className="row">
                <Button label="Düzenle" icon="pi pi-pencil" size="small" severity="secondary" outlined onClick={() => dlg.openEdit(u.id)} />
                <Button label="Rol değiştir" icon="pi pi-key" size="small" severity="secondary" outlined onClick={() => setRoleOpen(true)} />
                {u.isActive ? (
                  <Button label="Pasife al" size="small" severity="danger" outlined onClick={() => setActive(u, false)} />
                ) : (
                  <Button label="Aktifleştir" size="small" onClick={() => setActive(u, true)} />
                )}
              </div>
            }
          >
            <DefinitionList
              items={[
                ["E-posta", u.email],
                ["Telefon", u.phone || "—"],
                ["Rol", <StatusBadge key="r" group="role" value={u.role} />],
                ["Durum", <ActiveBadge key="a" value={u.isActive} />],
              ]}
            />
          </Card>
        )}
      </QueryState>
      <UserFormDialog visible={Boolean(dlg.editId)} editId={dlg.editId} fromList={user && String(user.id) === dlg.editId ? user : undefined} onHide={dlg.close} />
      {roleOpen && user && <RoleChangeDialog user={user} onHide={() => setRoleOpen(false)} />}
    </>
  );
}
