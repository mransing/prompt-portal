const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
const DEV_EMAIL = process.env.NEXT_PUBLIC_DEV_USER_EMAIL || "dev@local.test";

export type ApiError = {
  code: string;
  message: string;
  correlationId?: string;
  details?: unknown;
};

export class ApiClientError extends Error {
  code: string;
  correlationId?: string;
  status: number;

  constructor(status: number, body: ApiError) {
    super(body.message || "Request failed");
    this.code = body.code || "ERROR";
    this.correlationId = body.correlationId;
    this.status = status;
  }
}

function headers(json = true): HeadersInit {
  const h: Record<string, string> = {};
  if (json) h["Content-Type"] = "application/json";
  if (typeof window !== "undefined") {
    const email = localStorage.getItem("devUserEmail") || DEV_EMAIL;
    if (email) h["X-Dev-User-Email"] = email;
  } else if (DEV_EMAIL) {
    h["X-Dev-User-Email"] = DEV_EMAIL;
  }
  return h;
}

async function parse<T>(res: Response): Promise<T> {
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    throw new ApiClientError(res.status, data || { code: "ERROR", message: res.statusText });
  }
  return data as T;
}

export async function apiGet<T>(path: string): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    credentials: "include",
    headers: headers(false),
    cache: "no-store",
  });
  return parse<T>(res);
}

export async function apiSend<T>(path: string, method: string, body?: unknown): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    method,
    credentials: "include",
    headers: headers(true),
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  return parse<T>(res);
}

export async function apiUpload<T>(path: string, form: FormData, method = "POST"): Promise<T> {
  const h = headers(false) as Record<string, string>;
  delete h["Content-Type"];
  const res = await fetch(`${API_BASE}${path}`, {
    method,
    credentials: "include",
    headers: h,
    body: form,
  });
  return parse<T>(res);
}

export function mediaUrl(promptId: string, mediaId: string): string {
  return `${API_BASE}/api/prompts/${promptId}/media/${mediaId}`;
}

export function authLoginUrl(provider: "google" | "facebook"): string {
  return `${API_BASE}/oauth2/authorization/${provider}`;
}

export { API_BASE, DEV_EMAIL };
