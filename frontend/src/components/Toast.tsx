import { useCallback, useRef, useState, type ReactNode } from "react";
import { CloseIcon } from "../icons";
import { ToastContext, type ShowToast, type ToastTone } from "./toast-context";

type Toast = { id: number; text: string; tone: ToastTone };
const UPDATE_TEXT = "Доступна новая версия SelfCall";

export function ToastProvider({ children, dismissUpdate }: { children: ReactNode; dismissUpdate?: () => void }) {
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
      <div className="toasts">
        <div className="visually-hidden" role="status">{dismissUpdate ? UPDATE_TEXT : ""}</div>
        {dismissUpdate && (
          <div className="toast toast-update">
            <span className="toast-update-label">{UPDATE_TEXT}</span>
            <button className="btn btn-secondary btn-sm" type="button" onClick={() => window.location.reload()}>
              Обновить
            </button>
            <button className="toast-close" type="button" onClick={dismissUpdate} aria-label="Закрыть уведомление об обновлении">
              <CloseIcon />
            </button>
          </div>
        )}
        <div className="toast-list" role="status">
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
      </div>
    </ToastContext.Provider>
  );
}
