"use client";

import { useRef, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { Sidebar as PrimeSidebar } from "primereact/sidebar";
import { useAuth } from "@/providers/AuthProvider";
import { getMenuGroups, getBottomItems, findActiveItem } from "@/lib/rbac/menus";
import { ROLE_LABELS } from "@/lib/formatting/labels";
import { formatLongDate, formatDate, todayISO } from "@/lib/formatting/date";
import { Avatar } from "@/components/ui/Notice";
import { usePageMetaValue } from "@/components/layout/PageMeta";
import { usePendingCounts } from "@/features/requests/hooks/usePendingCounts";
import { useUnreadCount } from "@/features/notifications/hooks/useUnreadCount";
import styles from "./AppShell.module.scss";

function BrandMark({ size = 34 }) {
  return (
    <span className={styles.brandMark} style={{ width: size, height: size }} aria-hidden="true">
      <i className="pi pi-car" />
    </span>
  );
}

function CountPill({ value, mobile }) {
  if (!value) return null;
  return (
    <span className={mobile ? styles.countMobile : styles.count} aria-label={`${value} bekleyen`}>
      {value > 99 ? "99+" : value}
    </span>
  );
}

/**
 * Rol bazlı uygulama kabuğu. Masaüstü (≥900 px): gruplu koyu kenar menü + üst çubuk.
 * Mobil (<900 px): üst çubuk + 4 öğeli alt menü + "Daha fazla" alttan sayfası.
 */
export default function AppShell({ children }) {
  const { user, logout } = useAuth();
  const pathname = usePathname() || "/";
  const router = useRouter();
  const meta = usePageMetaValue();
  const [moreOpen, setMoreOpen] = useState(false);
  const moreButton = useRef(null);

  const role = user.role;
  const groups = getMenuGroups(role);
  const bottom = getBottomItems(role);
  const active = findActiveItem(role, pathname);
  const isAdmin = role === "ADMIN";
  const counts = usePendingCounts({ enabled: isAdmin }).data ?? {};
  const unread = useUnreadCount().data ?? 0;

  const title = meta.title || active?.label || "ServisFlow";
  const today = todayISO();
  const homeHref = groups[0]?.items[0]?.href ?? "/dashboard";
  const bottomKeys = bottom.map((b) => b.key);
  const moreActive = !active || !bottomKeys.includes(active.key);

  const navLink = (item, { mobile = false } = {}) => {
    const isActive = active?.key === item.key;
    return (
      <Link
        key={item.key}
        href={item.href}
        className={mobile ? `${styles.sheetLink} ${isActive ? styles.sheetLinkActive : ""}` : `${styles.navLink} ${isActive ? styles.navLinkActive : ""}`}
        aria-current={isActive ? "page" : undefined}
        onClick={mobile ? () => setMoreOpen(false) : undefined}
      >
        <i className={`pi ${item.icon}`} aria-hidden="true" />
        <span>{item.label}</span>
        {item.countKey && <CountPill value={counts[item.countKey]} />}
      </Link>
    );
  };

  return (
    <div className={styles.app}>
      <a href="#icerik" className={styles.skip}>
        İçeriğe geç
      </a>

      <aside className={styles.sidebar} aria-label="Ana menü">
        <Link href={homeHref} className={styles.brand}>
          <BrandMark />
          <span>
            <span className={styles.brandName}>ServisFlow</span>
            <span className={styles.brandSub}>{ROLE_LABELS[role]} paneli</span>
          </span>
        </Link>
        <nav className={styles.nav}>
          {groups.map((g) => (
            <div className={styles.navGroup} key={g.group}>
              <div className={styles.navGroupTitle}>{g.group}</div>
              {g.items.map((item) => navLink(item))}
            </div>
          ))}
        </nav>
        <div className={styles.sidebarFoot}>
          <button type="button" className={styles.logout} onClick={logout}>
            <i className="pi pi-sign-out" aria-hidden="true" /> Çıkış yap
          </button>
        </div>
      </aside>

      <div className={styles.main}>
        <header className={styles.topbar}>
          <div className={styles.topTitles}>
            <div className={styles.crumbs}>{meta.crumb || (active && groups.find((g) => g.items.includes(active))?.group) || ROLE_LABELS[role]}</div>
            <h1 className={styles.pageTitle}>{title}</h1>
          </div>
          <div className={styles.topRight}>
            <span className={styles.todayChip}>
              <i className="pi pi-calendar" aria-hidden="true" />
              {formatLongDate(today)}
            </span>
            <Link href="/bildirimler" className={styles.iconBtn} aria-label={`Bildirimler${unread ? `, ${unread} okunmamış` : ""}`}>
              <i className="pi pi-bell" aria-hidden="true" />
              {unread > 0 && <span className={styles.dot}>{unread > 99 ? "99+" : unread}</span>}
            </Link>
            <Link href="/profil" className={styles.userChip}>
              <Avatar name={user.fullName} />
              <span className={styles.who}>
                <b>{user.fullName}</b>
                <span>{ROLE_LABELS[role]}</span>
              </span>
            </Link>
          </div>
        </header>

        <header className={styles.mTop}>
          {meta.back ? (
            <button type="button" className={styles.iconBtn} aria-label="Geri" onClick={() => router.push(meta.back)}>
              <i className="pi pi-chevron-left" aria-hidden="true" />
            </button>
          ) : (
            <Link href={homeHref} aria-label="Ana ekran">
              <BrandMark size={32} />
            </Link>
          )}
          <div className={styles.mTitles}>
            <div className={styles.mTitle}>{title}</div>
            <div className={styles.mSub}>{meta.subtitle || `${formatDate(today)} · ${ROLE_LABELS[role]}`}</div>
          </div>
          <Link href="/bildirimler" className={styles.iconBtn} aria-label={`Bildirimler${unread ? `, ${unread} okunmamış` : ""}`}>
            <i className="pi pi-bell" aria-hidden="true" />
            {unread > 0 && <span className={styles.dot}>{unread > 99 ? "99+" : unread}</span>}
          </Link>
        </header>

        <main id="icerik" className={styles.content} tabIndex={-1}>
          {children}
        </main>

        <nav className={styles.bottomNav} aria-label="Alt menü">
          {bottom.map((item) => {
            const isActive = active?.key === item.key;
            return (
              <Link key={item.key} href={item.href} className={isActive ? styles.bottomActive : undefined} aria-current={isActive ? "page" : undefined}>
                <i className={`pi ${item.icon}`} aria-hidden="true" />
                {item.countKey && <CountPill value={counts[item.countKey]} mobile />}
                <span>{item.short || item.label}</span>
              </Link>
            );
          })}
          <button
            type="button"
            ref={moreButton}
            className={moreActive ? styles.bottomActive : undefined}
            onClick={() => setMoreOpen(true)}
            aria-haspopup="dialog"
            aria-expanded={moreOpen}
          >
            <i className="pi pi-bars" aria-hidden="true" />
            <span>Daha fazla</span>
          </button>
        </nav>
      </div>

      <PrimeSidebar
        visible={moreOpen}
        position="bottom"
        onHide={() => {
          setMoreOpen(false);
          moreButton.current?.focus();
        }}
        className={styles.sheet}
        ariaCloseLabel="Menüyü kapat"
        blockScroll
        header={
          <div className={styles.sheetUser}>
            <Avatar name={user.fullName} />
            <div>
              <b>{user.fullName}</b>
              <div className="text-xs muted">{ROLE_LABELS[role]}</div>
            </div>
          </div>
        }
      >
        <nav aria-label="Tüm menü">
          {groups.map((g) => (
            <div key={g.group}>
              <h4 className={styles.sheetGroup}>{g.group}</h4>
              {g.items.map((item) => navLink(item, { mobile: true }))}
            </div>
          ))}
          <h4 className={styles.sheetGroup}>Oturum</h4>
          <button type="button" className={styles.sheetLink} onClick={logout}>
            <i className="pi pi-sign-out" aria-hidden="true" />
            <span>Çıkış yap</span>
          </button>
        </nav>
      </PrimeSidebar>
    </div>
  );
}
