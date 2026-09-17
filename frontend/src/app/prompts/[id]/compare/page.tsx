"use client";

import { useParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import * as Diff from "diff";
import { AppNav } from "@/components/AppNav";
import { apiGet } from "@/lib/api";
import type { VersionSummary } from "@/lib/types";

type CompareResponse = {
  leftVersion: number;
  rightVersion: number;
  leftBody: string;
  rightBody: string;
  leftNegative?: string;
  rightNegative?: string;
  leftParameters?: string;
  rightParameters?: string;
};

export default function ComparePage() {
  const { id } = useParams<{ id: string }>();
  const [versions, setVersions] = useState<VersionSummary[]>([]);
  const [left, setLeft] = useState(1);
  const [right, setRight] = useState(1);
  const [data, setData] = useState<CompareResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    apiGet<VersionSummary[]>(`/api/prompts/${id}/versions`)
      .then((v) => {
        setVersions(v);
        if (v.length) {
          setRight(v[0].versionNumber);
          setLeft(v[v.length - 1]?.versionNumber ?? v[0].versionNumber);
        }
      })
      .catch((e) => setError(e instanceof Error ? e.message : "Failed"));
  }, [id]);

  async function run() {
    setError(null);
    try {
      setData(await apiGet<CompareResponse>(`/api/prompts/${id}/compare?left=${left}&right=${right}`));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Compare failed");
    }
  }

  const parts = useMemo(() => {
    if (!data) return [];
    return Diff.diffWords(data.leftBody || "", data.rightBody || "");
  }, [data]);

  return (
    <>
      <AppNav />
      <main className="container">
        <h1>Compare versions</h1>
        {error && <div className="error">{error}</div>}
        <div className="card" style={{ display: "flex", gap: 12, flexWrap: "wrap", alignItems: "end" }}>
          <div className="field" style={{ marginBottom: 0 }}>
            <label>Left</label>
            <select value={left} onChange={(e) => setLeft(Number(e.target.value))}>
              {versions.map((v) => (
                <option key={v.id} value={v.versionNumber}>
                  v{v.versionNumber}
                </option>
              ))}
            </select>
          </div>
          <div className="field" style={{ marginBottom: 0 }}>
            <label>Right</label>
            <select value={right} onChange={(e) => setRight(Number(e.target.value))}>
              {versions.map((v) => (
                <option key={v.id} value={v.versionNumber}>
                  v{v.versionNumber}
                </option>
              ))}
            </select>
          </div>
          <button className="btn btn-primary" type="button" onClick={run}>
            Diff
          </button>
        </div>

        {data && (
          <section className="card" style={{ marginTop: 16 }}>
            <h2 style={{ marginTop: 0 }}>
              Body: v{data.leftVersion} → v{data.rightVersion}
            </h2>
            <pre style={{ whiteSpace: "pre-wrap" }}>
              {parts.map((part, i) => (
                <span key={i} className={part.added ? "diff-add" : part.removed ? "diff-del" : undefined}>
                  {part.value}
                </span>
              ))}
            </pre>
          </section>
        )}
      </main>
    </>
  );
}
