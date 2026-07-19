"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { apiGet, API_BASE } from "@/lib/api";
import type { StorageUsage, UserInfo } from "@/lib/types";

export function AppNav() {
  const [user, setUser] = useState<UserInfo | null>(null);
  const [storage, setStorage] = useState<StorageUsage | null>(null);

  useEffect(() => {
    apiGet<UserInfo>("/api/auth/me")
      .then(setUser)
      .catch(() => setUser(null));
    apiGet<StorageUsage>("/api/storage")
      .then(setStorage)
      .catch(() => setStorage(null));
  }, []);

  async function logout() {
    await fetch(`${API_BASE}/api/auth/logout`, { method: "POST", credentials: "include" });
    localStorage.removeItem("devUserEmail");
    window.location.href = "/login";
  }

  const usedMb = storage ? (storage.usedBytes / (1024 * 1024)).toFixed(2) : "—";
  const quotaMb = storage ? (storage.quotaBytes / (1024 * 1024)).toFixed(0) : "50";
  const pct = storage ? Math.min(100, Math.round(storage.usedRatio * 100)) : 0;

  return (
    <header className="nav">
      <Link className="brand" href="/library">
        Prompt Portal
      </Link>
      <nav className="links">
        <Link href="/library">Library</Link>
        <Link href="/prompts/new">New</Link>
        <Link href="/settings">Settings</Link>
        {storage && (
          <span className="muted" title="Image storage quota">
            {usedMb} / {quotaMb} MB ({pct}%)
          </span>
        )}
        {user ? (
          <>
            <span className="muted">{user.email}</span>
            <button className="btn" type="button" onClick={logout}>
              Log out
            </button>
          </>
        ) : (
          <Link className="btn" href="/login">
            Log in
          </Link>
        )}
      </nav>
    </header>
  );
}
