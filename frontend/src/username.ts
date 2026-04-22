const KEY = "selfcall.username";

export function getUsername(): string {
  return localStorage.getItem(KEY) ?? "";
}

export function setUsername(name: string): void {
  localStorage.setItem(KEY, name);
}

export function clearUsername(): void {
  localStorage.removeItem(KEY);
}
