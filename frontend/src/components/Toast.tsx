import { useCallback, useRef, useState, type ReactNode } from "react";
import { CloseIcon } from "../icons";
import { ToastContext, type ShowToast, type ToastTone } from "./toast-context";

type Toast = { id: number; text: string; tone: ToastTone };

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const nextId = useRef(1);

  const show = useCallback<ShowToast>((text, tone = "info") => {
    const id = nextId.current++;
    // Same text twice in a row replaces the old one instead of stacking
    setToasts((list) => [...list.filter((t) => t.text !== text), { id, text, tone }]);
    // Errors stay until closed: people need time to read what to do next
    if (tone === "info") window.setTimeout(() => dismiss(id), 2500);
  }, []);

  function dismiss(id: number) {
    setToasts((list) => list.filter((t) => t.id !== id));
  }

  return (
    <ToastContext.Provider value={show}>
      {children}
      <div className="toasts" role="status" aria-live="polite">
        {toasts.map((t) => (
          <div key={t.id} className={`toast toast-${t.tone}`}>
            <span>{t.text}</span>
            {t.tone === "error" && (
              <button className="toast-close" onClick={() => dismiss(t.id)} aria-label="Закрыть уведомление">
                <CloseIcon />
              </button>
            )}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
