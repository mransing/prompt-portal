"use client";

import { useEffect, useState } from "react";
import { AppNav } from "@/components/AppNav";
import { QuotaBar } from "@/components/QuotaBar";
import { apiGet, DEV_EMAIL } from "@/lib/api";
import type { StorageUsage, UserInfo } from "@/lib/types";

export default function SettingsPage() {
  const [user, setUser] = useState<UserInfo | null>(null);
  const [storage, setStorage] = useState<StorageUsage | null>(null);
  const [email, setEmail] = useState("");

  useEffect(() => {
    setEmail(localStorage.getItem("devUserEmail") || DEV_EMAIL);
    apiGet<UserInfo>("/api/auth/me").then(setUser).catch(() => setUser(null));
    apiGet<StorageUsage>("/api/storage").then(setStorage).catch(() => setStorage(null));
  }, []);

  function saveDevEmail() {
    localStorage.setItem("devUserEmail", email.trim());
    window.location.reload();
  }

  return (
    <>
      <AppNav />
      <main className="container">
        <h1>Settings</h1>
        <section className="card">
          <h2 style={{ marginTop: 0 }}>Account</h2>
          {user ? (
            <ul className="muted">
              <li>Email: {user.email}</li>
              <li>Name: {user.name}</li>
              <li>Provider: {user.provider}</li>
              <li>Dev mode API: {user.devMode ? "yes" : "no"}</li>
            </ul>
          ) : (
            <p className="muted">Not signed in.</p>
          )}
          <div className="field">
            <label>Dev user email (X-Dev-User-Email)</label>
            <input value={email} onChange={(e) => setEmail(e.target.value)} />
          </div>
          <button className="btn" type="button" onClick={saveDevEmail}>
            Save dev email
          </button>
        </section>

        <section className="card" style={{ marginTop: 16 }}>
          <h2 style={{ marginTop: 0 }}>Storage</h2>
          <QuotaBar storage={storage} />
          <p className="muted">Images only count toward the 50 MB quota. Prompt text is unlimited for MVP.</p>
        </section>

        <section className="card" style={{ marginTop: 16 }}>
          <h2 style={{ marginTop: 0 }}>Retention</h2>
          <p>
            Unmodified prompts and their images may be deleted after <strong>2 years</strong>. You are solely
            responsible for backing up content you care about.
          </p>
          <p className="muted">
            Backup: <code>mongodump</code> for MongoDB + copy the upload folder.
          </p>
        </section>
      </main>
    </>
  );
}
