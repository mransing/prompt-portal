"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AppNav } from "@/components/AppNav";
import { QuotaBar } from "@/components/QuotaBar";
import { apiGet, mediaUrl } from "@/lib/api";
import type { Page, PromptSummary, StorageUsage } from "@/lib/types";

export default function LibraryPage() {
  const [q, setQ] = useState("");
  const [status, setStatus] = useState("all");
  const [focus, setFocus] = useState("");
  const [hasMedia, setHasMedia] = useState("");
  const [page, setPage] = useState<Page<PromptSummary> | null>(null);
  const [storage, setStorage] = useState<StorageUsage | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const params = new URLSearchParams();
      if (q.trim()) params.set("q", q.trim());
      if (status) params.set("status", status);
      if (focus) params.set("mediaTypeFocus", focus);
      if (hasMedia === "yes") params.set("hasMedia", "true");
      if (hasMedia === "no") params.set("hasMedia", "false");
      params.set("sort", "updated");
      params.set("size", "50");
      const data = await apiGet<Page<PromptSummary>>(`/api/prompts?${params}`);
      setPage(data);
      setStorage(await apiGet<StorageUsage>("/api/storage"));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load library");
      if (String(e).includes("401") || (e as { status?: number }).status === 401) {
        window.location.href = "/login";
      }
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <>
      <AppNav />
      <main className="container">
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12 }}>
          <h1 style={{ margin: 0 }}>Library</h1>
          <Link className="btn btn-primary" href="/prompts/new">
            New prompt
          </Link>
        </div>

        <div className="card" style={{ marginTop: 16 }}>
          <QuotaBar storage={storage} />
          <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr 1fr 1fr auto", gap: 8 }}>
            <input placeholder="Search title, notes, body…" value={q} onChange={(e) => setQ(e.target.value)} />
            <select value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="all">All statuses</option>
              <option value="draft">Draft</option>
              <option value="active">Active</option>
              <option value="archived">Archived</option>
            </select>
            <select value={focus} onChange={(e) => setFocus(e.target.value)}>
              <option value="">Any focus</option>
              <option value="image">Image</option>
              <option value="video">Video (tag)</option>
              <option value="mixed">Mixed</option>
              <option value="text-only">Text only</option>
            </select>
            <select value={hasMedia} onChange={(e) => setHasMedia(e.target.value)}>
              <option value="">Any media</option>
              <option value="yes">Has images</option>
              <option value="no">No images</option>
            </select>
            <button className="btn btn-primary" type="button" onClick={load}>
              Search
            </button>
          </div>
        </div>

        {error && <div className="error">{error}</div>}
        {loading && <p className="muted">Loading…</p>}

        {!loading && page && page.content.length === 0 && (
          <div className="card" style={{ marginTop: 16 }}>
            <p>No prompts yet. Create your first one.</p>
            <Link className="btn btn-primary" href="/prompts/new">
              New prompt
            </Link>
          </div>
        )}

        <div className="grid" style={{ marginTop: 16 }}>
          {page?.content.map((p) => (
            <Link key={p.id} href={`/prompts/${p.id}`} className="card" style={{ color: "inherit", textDecoration: "none" }}>
              {p.thumbnailMediaId ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img
                  className="thumb"
                  alt=""
                  src={mediaUrl(p.id, p.thumbnailMediaId)}
                  style={{ marginBottom: 10 }}
                />
              ) : (
                <div className="thumb" style={{ display: "grid", placeItems: "center", color: "#6d8299" }}>
                  No image
                </div>
              )}
              <strong>{p.title}</strong>
              <div style={{ marginTop: 6, display: "flex", gap: 6, flexWrap: "wrap" }}>
                <span className="badge">{p.status}</span>
                <span className="badge">v{p.currentVersion}</span>
                <span className="badge">{p.mediaTypeFocus}</span>
                <span className="badge">{p.mediaCount} img</span>
              </div>
              <p className="muted" style={{ fontSize: "0.85rem", marginBottom: 0 }}>
                Updated {new Date(p.updatedAt).toLocaleString()}
              </p>
            </Link>
          ))}
        </div>
      </main>
    </>
  );
}
