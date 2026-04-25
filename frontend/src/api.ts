import { API_BASE_URL } from "./config";

export type Room = { name: string; display: string };

export type TokenResponse = {
  token: string;
  url: string;
  room: string;
  identity: string;
};

export async function fetchRooms(): Promise<Room[]> {
  const r = await fetch(`${API_BASE_URL}/rooms`);
  if (!r.ok) throw new Error(`GET /rooms failed: ${r.status}`);
  const data: { rooms: Room[] } = await r.json();
  return data.rooms;
}

export async function fetchToken(
  room: string,
  username: string,
): Promise<TokenResponse> {
  const r = await fetch(`${API_BASE_URL}/token`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ room, username }),
  });
  if (!r.ok) {
    const text = await r.text();
    throw new Error(`POST /token failed: ${r.status} ${text}`);
  }
  return r.json();
}
