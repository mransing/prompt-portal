"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { AppNav } from "@/components/AppNav";
import { apiSend, apiUpload } from "@/lib/api";
import { ACCEPT_IMAGES, validateImageFile } from "@/lib/imageValidation";
import type { PromptDetail } from "@/lib/types";

export default function NewPromptPage() {
  const router = useRouter();
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [status, setStatus] = useState("draft");
  const [mediaTypeFocus, setMediaTypeFocus] = useState("image");
  const [tags, setTags] = useState("");
  const [body, setBody] = useState("");
  const [negativePrompt, setNegativePrompt] = useState("");
  const [parametersJson, setParametersJson] = useState("");
  const [files, setFiles] = useState<File[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  function onFiles(list: FileList | null) {
    if (!list) return;
    const next: File[] = [];
    for (const f of Array.from(list)) {
      const err = validateImageFile(f);
      if (err) {
        setError(err);
        return;
      }
      next.push(f);
    }
    setError(null);
    setFiles((prev) => [...prev, ...next]);
  }

  async function save(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const created = await apiSend<PromptDetail>("/api/prompts", "POST", {
        title,
        description,
        status,
        mediaTypeFocus,
        tags: tags
          .split(",")
          .map((t) => t.trim())
          .filter(Boolean),
        body,
        negativePrompt,
        parametersJson,
      });
      if (files.length) {
        const form = new FormData();
        files.forEach((f) => form.append("files", f));
        await apiUpload(`/api/prompts/${created.id}/media`, form);
      }
      router.push(`/prompts/${created.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Save failed");
    } finally {
      setSaving(false);
    }
  }

  return (
    <>
      <AppNav />
      <main className="container">
        <h1>New prompt</h1>
        <form className="card" onSubmit={save}>
          {error && <div className="error">{error}</div>}
          <div className="field">
            <label>Title</label>
            <input value={title} onChange={(e) => setTitle(e.target.value)} required maxLength={200} />
          </div>
          <div className="field">
            <label>Description / notes</label>
            <textarea value={description} onChange={(e) => setDescription(e.target.value)} style={{ minHeight: 80 }} />
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 12 }}>
            <div className="field">
              <label>Status</label>
              <select value={status} onChange={(e) => setStatus(e.target.value)}>
                <option value="draft">Draft</option>
                <option value="active">Active</option>
              </select>
            </div>
            <div className="field">
              <label>Media type focus</label>
              <select value={mediaTypeFocus} onChange={(e) => setMediaTypeFocus(e.target.value)}>
                <option value="image">Image</option>
                <option value="video">Video (tag only)</option>
                <option value="mixed">Mixed</option>
                <option value="text-only">Text only</option>
              </select>
            </div>
            <div className="field">
              <label>Tags (comma-separated)</label>
              <input value={tags} onChange={(e) => setTags(e.target.value)} />
            </div>
          </div>
          <div className="field">
            <label>Prompt text</label>
            <textarea value={body} onChange={(e) => setBody(e.target.value)} required />
          </div>
          <div className="field">
            <label>Negative prompt</label>
            <textarea value={negativePrompt} onChange={(e) => setNegativePrompt(e.target.value)} style={{ minHeight: 80 }} />
          </div>
          <div className="field">
            <label>Parameters (JSON)</label>
            <textarea value={parametersJson} onChange={(e) => setParametersJson(e.target.value)} style={{ minHeight: 80 }} />
          </div>
          <div className="field">
            <label>Result images (optional, ≤ 2 MB each, PNG/JPEG/WebP/GIF)</label>
            <div className="dropzone">
              <input
                type="file"
                accept={ACCEPT_IMAGES}
                multiple
                onChange={(e) => onFiles(e.target.files)}
              />
              {files.length > 0 && (
                <p className="muted" style={{ marginBottom: 0 }}>
                  {files.length} file(s) ready: {files.map((f) => f.name).join(", ")}
                </p>
              )}
            </div>
          </div>
          <button className="btn btn-primary" type="submit" disabled={saving}>
            {saving ? "Saving…" : "Save"}
          </button>
        </form>
      </main>
    </>
  );
}
