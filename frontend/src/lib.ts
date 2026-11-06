/** Small helpers: meeting links, saved preferences, clipboard, plural forms. */

const ID_RE = /([a-z]{3})-?([a-z]{4})-?([a-z]{3})/;

export function meetingUrl(id: string): string {
  return `${window.location.origin}/m/${id}`;
}

/** Accepts a full link, a path or a bare code (with or without dashes). */
export function parseMeetingId(input: string): string | null {
  const text = input.trim().toLowerCase();
  const fromPath = text.match(/\/m\/([a-z-]+)/);
  const m = (fromPath ? fromPath[1] : text).match(ID_RE);
  return m ? `${m[1]}-${m[2]}-${m[3]}` : null;
}

// ----- Saved preferences (per browser). Storage can be blocked, so never throw. -----

type Prefs = {
  name: string;
  micOn: boolean;
  camOn: boolean;
  micId?: string;
  camId?: string;
};

const PREFS_KEY = "selfcall.prefs";
const DEFAULT_PREFS: Prefs = { name: "", micOn: true, camOn: true };

export function loadPrefs(): Prefs {
  try {
    const raw = localStorage.getItem(PREFS_KEY);
    return raw ? { ...DEFAULT_PREFS, ...JSON.parse(raw) } : DEFAULT_PREFS;
  } catch {
    return DEFAULT_PREFS;
  }
}

export function savePrefs(patch: Partial<Prefs>): void {
  try {
    localStorage.setItem(PREFS_KEY, JSON.stringify({ ...loadPrefs(), ...patch }));
  } catch {
    // private mode or blocked storage — preferences just won't be remembered
  }
}

// ----- Clipboard -----

export async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    // Fallback for non-secure contexts (http on a LAN address)
    const ta = document.createElement("textarea");
    ta.value = text;
    ta.style.position = "fixed";
    ta.style.opacity = "0";
    document.body.appendChild(ta);
    ta.select();
    const ok = document.execCommand("copy");
    ta.remove();
    return ok;
  }
}

/** Opens the system share sheet on phones; returns false where it isn't available. */
export async function shareLink(url: string): Promise<boolean> {
  if (!navigator.share || !window.matchMedia("(pointer: coarse)").matches) return false;
  try {
    await navigator.share({ title: "Видеовстреча", url });
    return true;
  } catch {
    return false;
  }
}

// ----- Russian plurals: plural(3, ["человек", "человека", "человек"]) -----

export function plural(n: number, forms: [string, string, string]): string {
  const mod10 = n % 10;
  const mod100 = n % 100;
  if (mod10 === 1 && mod100 !== 11) return forms[0];
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return forms[1];
  return forms[2];
}

export const canShareScreen: boolean =
  typeof navigator !== "undefined" &&
  !!navigator.mediaDevices &&
  "getDisplayMedia" in navigator.mediaDevices;
