import { useEffect, useState } from "react";

const CHECK_INTERVAL_MS = 60_000;

export function useReleaseUpdate() {
  const [availableRelease, setAvailableRelease] = useState<string>();
  const [dismissedRelease, setDismissedRelease] = useState<string>();

  useEffect(() => {
    const currentRelease = document.querySelector<HTMLMetaElement>('meta[name="selfcall-release"]')?.content;
    if (!currentRelease) return;

    const lifetime = new AbortController();
    let checking = false;
    const check = async () => {
      if (document.visibilityState !== "visible" || checking) return;
      checking = true;
      try {
        const response = await fetch("/version.json", { cache: "no-store", signal: lifetime.signal });
        if (!response.ok) return;
        const data = await response.json();
        const release: unknown = data?.release;
        if (!lifetime.signal.aborted && typeof release === "string" && release) {
          setAvailableRelease(release === currentRelease ? undefined : release);
        }
      } catch {
        // A temporary failure can be retried on the next check.
      } finally {
        checking = false;
      }
    };

    void check();
    const timer = window.setInterval(check, CHECK_INTERVAL_MS);
    document.addEventListener("visibilitychange", check);
    window.addEventListener("focus", check);
    return () => {
      lifetime.abort();
      window.clearInterval(timer);
      document.removeEventListener("visibilitychange", check);
      window.removeEventListener("focus", check);
    };
  }, []);

  return availableRelease && availableRelease !== dismissedRelease
    ? () => setDismissedRelease(availableRelease)
    : undefined;
}
