"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { API_BASE, apiGet, DEV_EMAIL } from "@/lib/api";

type Providers = {
  google: boolean;
  facebook: boolean;
  devMode: boolean;
  googleLoginUrl?: string | null;
  facebookLoginUrl?: string | null;
};

export default function LoginPage() {
  const router = useRouter();
  const [providers, setProviders] = useState<Providers | null>(null);
  const [email, setEmail] = useState(DEV_EMAIL);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    apiGet<Providers>("/api/auth/providers")
      .then(setProviders)
      .catch(() =>
        setProviders({ google: false, facebook: false, devMode: true, googleLoginUrl: null, facebookLoginUrl: null })
      );
  }, []);

  async function devLogin(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    localStorage.setItem("devUserEmail", email.trim());
    try {
      await apiGet("/api/auth/me");
      router.push("/library");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Login failed");
    }
  }

  return (
    <main className="container" style={{ maxWidth: 480, marginTop: "4rem" }}>
      <div className="card">
        <h1 style={{ marginTop: 0 }}>Prompt Portal</h1>
        <p className="muted">Version-controlled prompts for images and video-oriented text.</p>

        {error && <div className="error">{error}</div>}

        {providers?.google && (
          <a className="btn btn-primary" style={{ width: "100%", marginBottom: 10 }} href={`${API_BASE}/oauth2/authorization/google`}>
            Continue with Google
          </a>
        )}
        {providers?.facebook && (
          <a className="btn" style={{ width: "100%", marginBottom: 10 }} href={`${API_BASE}/oauth2/authorization/facebook`}>
            Continue with Facebook
          </a>
        )}

        {(providers?.devMode || (!providers?.google && !providers?.facebook)) && (
          <form onSubmit={devLogin} style={{ marginTop: 16 }}>
            <p className="muted" style={{ fontSize: "0.9rem" }}>
              Dev mode: authenticate with an allowlisted email header (no OAuth secrets required).
            </p>
            <div className="field">
              <label htmlFor="email">Email</label>
              <input id="email" value={email} onChange={(e) => setEmail(e.target.value)} type="email" required />
            </div>
            <button className="btn btn-primary" type="submit" style={{ width: "100%" }}>
              Continue (dev)
            </button>
          </form>
        )}
      </div>
    </main>
  );
}
