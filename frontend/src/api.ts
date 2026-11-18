import { API_BASE_URL } from "./config";

export type MeetingInfo = {
  id: string;
  active: boolean;
  participants: number;
  max_participants: number;
};

export type JoinResponse = {
  token: string;
  url: string;
  meeting_id: string;
  identity: string;
  name: string;
  elapsed_ms?: number;
};

export type CallContact = { login: string; name: string };

export type DirectCall = {
  id: string;
  status: "ringing" | "accepted" | "declined" | "cancelled" | "expired" | "unreachable" | "ended";
  remaining_ms: number;
};

/** Stable error codes from the backend, plus client-side ones. */
export type ApiErrorCode =
  | "meeting_not_found"
  | "server_busy"
  | "meeting_full"
  | "too_many_requests"
  | "sign_in_required"
  | "calls_unavailable"
  | "call_busy"
  | "call_not_found"
  | "call_finished"
  | "not_available"
  | "network"
  | "unknown";

export class ApiError extends Error {
  readonly code: ApiErrorCode;
  constructor(code: ApiErrorCode, message: string) {
    super(message);
    this.code = code;
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let r: Response;
  try {
    r = await fetch(`${API_BASE_URL}/api${path}`, {
      ...init,
      headers: { "Content-Type": "application/json", ...init?.headers },
    });
  } catch {
    throw new ApiError("network", "Нет связи с сервером");
  }
  if (r.ok) return r.json() as Promise<T>;

  let code: ApiErrorCode = r.status === 401 ? "sign_in_required" : "unknown";
  let message = `Ошибка сервера (${r.status})`;
  try {
    const body = await r.json();
    if (body?.detail?.code) {
      code = body.detail.code;
      message = body.detail.message ?? message;
    }
  } catch {
    // not JSON — keep the generic message
  }
  throw new ApiError(code, message);
}

export function createMeeting(): Promise<{ id: string }> {
  return request("/meetings", { method: "POST" });
}

export function getMeeting(id: string): Promise<MeetingInfo> {
  return request(`/meetings/${encodeURIComponent(id)}`);
}

export function joinMeeting(id: string, name: string): Promise<JoinResponse> {
  return request(`/meetings/${encodeURIComponent(id)}/join`, {
    method: "POST",
    body: JSON.stringify({ name: name.trim() || null }),
  });
}

export function getCallDirectory(signal: AbortSignal): Promise<{ contacts: CallContact[] }> {
  return request("/calls/directory", { signal, cache: "no-store" });
}

export function dialColleague(id: string, login: string): Promise<DirectCall> {
  return request("/calls/dial", {
    method: "POST",
    body: JSON.stringify({ id, login }),
    signal: AbortSignal.timeout(20_000),
  });
}

export function getDirectCall(id: string): Promise<DirectCall> {
  return request(`/calls/${id}`, { cache: "no-store", signal: AbortSignal.timeout(5_000) });
}

export function connectDirectCall(id: string): Promise<JoinResponse> {
  return request(`/calls/${id}/connect`, { method: "POST", signal: AbortSignal.timeout(15_000) });
}

export function closeDirectCall(id: string, action: "cancel" | "end", keepalive = false): Promise<DirectCall> {
  return request(`/calls/${id}/state/${action}`, {
    method: "POST",
    keepalive,
    signal: keepalive ? undefined : AbortSignal.timeout(10_000),
  });
}
