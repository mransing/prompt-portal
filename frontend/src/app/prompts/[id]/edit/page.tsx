"use client";

import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { AppNav } from "@/components/AppNav";
import { apiGet, apiSend, apiUpload } from "@/lib/api";
import { ACCEPT_IMAGES, validateImageFile } from "@/lib/imageValidation";
import type { PromptDetail } from "@/lib/types";

export default function EditPromptPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [status, setStatus] = useState("draft");
  const [mediaTypeFocus, setMediaTypeFocus] = useState("image");
  const [tags, setTags] = useState("");
  const [body, setBody] = useState("");
  const [negativePrompt, setNegativePrompt] = useState("");
  const [parametersJson, setParametersJson] = useState("");
  const [changeSummary, setChangeSummary] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    apiGet<PromptDetail>(`/api/prompts/${id}`)
      .then((p) => {
        setTitle(p.title);
        setDescription(p.description || "");
        setStatus(p.status);
        setMediaTypeFocus(p.mediaTypeFocus);
        setTags((p.tags || []).join(", "));
        setBody(p.latestVersion.body || "");
        setNegativePrompt(p.latestVersion.negativePrompt || "");
        setParametersJson(p.latestVersion.parametersJson || "");
      })
      .catch((e) => setError(e instanceof Error ? e.message : "Load failed"));
  }, [id]);

  async function save(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await apiSend(`/api/prompts/${id}`, "PATCH", {
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
        changeSummary,
      });
      router.push(`/prompts/${id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Save failed");
    } finally {
      setSaving(false);
    }
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
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Upload failed");
    }
  }

  return (
    <>
      <AppNav />
      <main className="container">
        <h1>Edit prompt</h1>
        <form className="card" onSubmit={save}>
          {error && <div className="error">{error}</div>}
          <div className="field">
            <label>Title</label>
            <input value={title} onChange={(e) => setTitle(e.target.value)} required />
          </div>
          <div className="field">
            <label>Description</label>
            <textarea value={description} onChange={(e) => setDescription(e.target.value)} style={{ minHeight: 80 }} />
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 12 }}>
            <div className="field">
              <label>Status</label>
              <select value={status} onChange={(e) => setStatus(e.target.value)}>
                <option value="draft">Draft</option>
                <option value="active">Active</option>
                <option value="archived">Archived</option>
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
              <label>Tags</label>
              <input value={tags} onChange={(e) => setTags(e.target.value)} />
            </div>
          </div>
          <div className="field">
            <label>Prompt text (changes create a new version)</label>
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
            <label>Change summary (optional)</label>
            <input value={changeSummary} onChange={(e) => setChangeSummary(e.target.value)} />
          </div>
          <div className="field">
            <label>Add images (no version bump)</label>
            <input type="file" accept={ACCEPT_IMAGES} multiple onChange={(e) => onUpload(e.target.files)} />
          </div>
          <button className="btn btn-primary" type="submit" disabled={saving}>
            {saving ? "Saving…" : "Save"}
          </button>
        </form>
      </main>
    </>
  );
}
