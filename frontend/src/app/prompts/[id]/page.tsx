"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { AppNav } from "@/components/AppNav";
import { apiGet, apiSend, apiUpload, mediaUrl } from "@/lib/api";
import { ACCEPT_IMAGES, validateImageFile } from "@/lib/imageValidation";
import type { PromptDetail, VersionSummary } from "@/lib/types";

export default function PromptDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [prompt, setPrompt] = useState<PromptDetail | null>(null);
  const [versions, setVersions] = useState<VersionSummary[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);

  async function load() {
    setError(null);
    try {
      setPrompt(await apiGet<PromptDetail>(`/api/prompts/${id}`));
      setVersions(await apiGet<VersionSummary[]>(`/api/prompts/${id}/versions`));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load");
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  async function copy(text: string) {
    await navigator.clipboard.writeText(text);
    setMsg("Copied to clipboard");
    setTimeout(() => setMsg(null), 1500);
  }

  async function archive() {
    if (!confirm("Archive this prompt?")) return;
    await apiSend(`/api/prompts/${id}/archive`, "POST");
    await load();
  }

  async function softDelete() {
    if (!confirm("Soft-delete this prompt?")) return;
    await apiSend(`/api/prompts/${id}`, "DELETE");
    router.push("/library");
  }

  async function restore(n: number) {
    if (!confirm(`Restore version ${n} as a new version?`)) return;
    await apiSend(`/api/prompts/${id}/versions/${n}/restore`, "POST");
    await load();
  }

  async function onUpload(files: FileList | null) {
    if (!files?.length) return;
    for (const f of Array.from(files)) {
      const err = validateImageFile(f);
      if (err) {
        setError(err);
        return;
      }
    }
    const form = new FormData();
    Array.from(files).forEach((f) => form.append("files", f));
    try {
      await apiUpload(`/api/prompts/${id}/media`, form);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Upload failed");
    }
  }

  async function removeMedia(mediaId: string) {
    if (!confirm("Remove this image?")) return;
    await apiSend(`/api/prompts/${id}/media/${mediaId}`, "DELETE");
    await load();
  }

  if (!prompt && !error) {
    return (
      <>
        <AppNav />
        <main className="container">
          <p className="muted">Loading…</p>
        </main>
      </>
    );
  }

  return (
    <>
      <AppNav />
      <main className="container">
        {error && <div className="error">{error}</div>}
        {msg && <p className="muted">{msg}</p>}
        {prompt && (
          <>
            <div style={{ display: "flex", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}>
              <div>
                <h1 style={{ marginBottom: 6 }}>{prompt.title}</h1>
                <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                  <span className="badge">{prompt.status}</span>
                  <span className="badge">v{prompt.currentVersion}</span>
                  <span className="badge">{prompt.mediaTypeFocus}</span>
                  {prompt.tags?.map((t) => (
                    <span className="badge" key={t}>
                      {t}
                    </span>
                  ))}
                </div>
              </div>
              <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                <Link className="btn" href={`/prompts/${id}/edit`}>
                  Edit
                </Link>
                <Link className="btn" href={`/prompts/${id}/compare`}>
                  Compare
                </Link>
                <button className="btn" type="button" onClick={() => copy(prompt.latestVersion.body)}>
                  Copy prompt
                </button>
                <button className="btn" type="button" onClick={archive}>
                  Archive
                </button>
                <button className="btn btn-danger" type="button" onClick={softDelete}>
                  Delete
                </button>
              </div>
            </div>

            {prompt.description && <p className="muted">{prompt.description}</p>}

            <section className="card" style={{ marginTop: 16 }}>
              <h2 style={{ marginTop: 0 }}>Current text</h2>
              <pre style={{ whiteSpace: "pre-wrap", margin: 0 }}>{prompt.latestVersion.body}</pre>
              {prompt.latestVersion.negativePrompt && (
                <>
                  <h3>Negative</h3>
                  <pre style={{ whiteSpace: "pre-wrap" }}>{prompt.latestVersion.negativePrompt}</pre>
                </>
              )}
              {prompt.latestVersion.parametersJson && (
                <>
                  <h3>Parameters</h3>
                  <pre style={{ whiteSpace: "pre-wrap" }}>{prompt.latestVersion.parametersJson}</pre>
                </>
              )}
            </section>

            <section className="card" style={{ marginTop: 16 }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <h2 style={{ margin: 0 }}>Result images</h2>
                <label className="btn">
                  Upload
                  <input
                    type="file"
                    accept={ACCEPT_IMAGES}
                    multiple
                    hidden
                    onChange={(e) => onUpload(e.target.files)}
                  />
                </label>
              </div>
              <div className="grid" style={{ marginTop: 12 }}>
                {prompt.media.map((m) => (
                  <div key={m.id}>
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img className="thumb" src={mediaUrl(prompt.id, m.id)} alt={m.fileName} />
                    <div className="muted" style={{ fontSize: "0.8rem" }}>
                      {m.width}×{m.height} · {(m.byteSize / 1024).toFixed(1)} KB
                    </div>
                    <button className="btn btn-danger" type="button" onClick={() => removeMedia(m.id)}>
                      Remove
                    </button>
                  </div>
                ))}
                {prompt.media.length === 0 && <p className="muted">No images attached.</p>}
              </div>
            </section>

            <section className="card" style={{ marginTop: 16 }}>
              <h2 style={{ marginTop: 0 }}>Version history</h2>
              <table className="table">
                <thead>
                  <tr>
                    <th>Version</th>
                    <th>Summary</th>
                    <th>When</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {versions.map((v) => (
                    <tr key={v.id}>
                      <td>v{v.versionNumber}</td>
                      <td>{v.changeSummary || "—"}</td>
                      <td>{new Date(v.createdAt).toLocaleString()}</td>
                      <td>
                        <button className="btn" type="button" onClick={() => restore(v.versionNumber)}>
                          Restore
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </section>
          </>
        )}
      </main>
    </>
  );
}
