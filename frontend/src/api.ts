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
};

/** Stable error codes from the backend, plus client-side ones. */
export type ApiErrorCode =
  | "meeting_not_found"
  | "server_busy"
  | "meeting_full"
  | "too_many_requests"
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

  let code: ApiErrorCode = "unknown";
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
