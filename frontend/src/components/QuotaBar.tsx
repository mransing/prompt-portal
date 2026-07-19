import type { StorageUsage } from "@/lib/types";

export function QuotaBar({ storage }: { storage: StorageUsage | null }) {
  if (!storage) return null;
  const pct = Math.min(100, Math.round(storage.usedRatio * 100));
  const usedMb = (storage.usedBytes / (1024 * 1024)).toFixed(2);
  const quotaMb = (storage.quotaBytes / (1024 * 1024)).toFixed(0);
  return (
    <div style={{ marginBottom: "1rem" }}>
      <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 6 }}>
        <span className="muted">Image storage</span>
        <span className="muted">
          {usedMb} / {quotaMb} MB
        </span>
      </div>
      <div className={`quota ${storage.warning ? "warn" : ""}`}>
        <span style={{ width: `${pct}%` }} />
      </div>
      {storage.warning && (
        <p className="warn" style={{ marginTop: 8 }}>
          You are using 90% or more of your 50 MB image quota.
        </p>
      )}
    </div>
  );
}
