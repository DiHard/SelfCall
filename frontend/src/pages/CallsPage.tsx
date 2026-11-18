import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import {
  ApiError, closeDirectCall, connectDirectCall, dialColleague, getCallDirectory, getDirectCall,
  type CallContact, type DirectCall, type JoinResponse,
} from "../api";
import { CallRoom, type LeaveReason } from "../components/CallRoom";
import { Logo } from "../components/Logo";
import { useFocusHeading } from "../components/useFocusHeading";
import { UsersIcon } from "../icons";

const CALL_ERRORS: Record<string, string> = {
  sign_in_required: "Перезагрузите страницу и войдите в SelfCall под своей учётной записью.",
  calls_unavailable: "Звонки сейчас недоступны. Попробуйте позже.",
  call_busy: "Один из участников уже разговаривает. Позвоните чуть позже.",
  call_not_found: "Вызов больше недоступен. Позвоните ещё раз.",
  call_finished: "Звонок уже завершён. Позвоните ещё раз.",
  not_available: "Коллега выключил входящие звонки.",
  server_busy: "На сервере уже идёт максимум встреч. Попробуйте через несколько минут.",
  too_many_requests: "Слишком много вызовов. Подождите минуту и позвоните снова.",
  network: "Нет связи с сервером. Проверьте интернет и попробуйте ещё раз.",
};

const CALL_RESULTS = {
  declined: "Коллега отклонил вызов",
  cancelled: "Вызов отменён",
  expired: "Коллега не ответил",
  unreachable: "Не удалось дозвониться",
  ended: "Звонок завершён",
};

function callError(error: unknown, fallback: string): string {
  return (error instanceof ApiError && CALL_ERRORS[error.code]) || fallback;
}

export function CallsPage() {
  const [colleague, setColleague] = useState<CallContact | null>(null);

  return colleague
    ? <OutgoingCall colleague={colleague} onBack={() => setColleague(null)} />
    : <CallDirectory onDial={setColleague} />;
}

function CallDirectory({ onDial }: { onDial: (colleague: CallContact) => void }) {
  const heading = useFocusHeading<HTMLHeadingElement>();
  const [contacts, setContacts] = useState<CallContact[] | null>(null);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  const nameCounts = new Map<string, number>();
  for (const { name } of contacts ?? []) {
    const key = name.toLowerCase();
    nameCounts.set(key, (nameCounts.get(key) ?? 0) + 1);
  }

  useEffect(() => {
    const controller = new AbortController();
    getCallDirectory(AbortSignal.any([controller.signal, AbortSignal.timeout(10_000)]))
      .then(({ contacts }) => {
        if (!controller.signal.aborted) setContacts(contacts);
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setError(callError(error, "Не удалось загрузить список коллег. Попробуйте обновить его."));
        }
      });
    return () => controller.abort();
  }, [revision]);

  const refresh = () => {
    setContacts(null);
    setError("");
    setRevision((value) => value + 1);
  };

  return (
    <main className="page">
      <header className="topbar"><Logo link /></header>
      <div className="colleagues-wrap">
        <section className="panel colleagues screen-enter" aria-labelledby="colleagues-title">
          <h1 id="colleagues-title" className="prejoin-title" ref={heading} tabIndex={-1}>Звонки коллегам</h1>
          {error ? <p role="alert" className="field-error">{error}</p> : !contacts?.length && (
            <p className="muted" role="status">
              {contacts === null ? "Загружаем список коллег…"
                : "Здесь появятся коллеги, которые включат входящие звонки в SelfCall на Android."}
            </p>
          )}
          {!!contacts?.length && (
            <ul className="colleague-list" aria-label="Коллеги">
              {contacts.map((contact) => {
                const duplicate = (nameCounts.get(contact.name.toLowerCase()) ?? 0) > 1;
                return (
                  <li key={contact.login} className="colleague">
                    <span className="colleague-name">
                      <strong>{contact.name}</strong>
                      {duplicate && <span className="muted">{contact.login}</span>}
                    </span>
                    <button className="btn btn-secondary btn-sm" onClick={() => onDial(contact)}
                      aria-label={duplicate ? `Позвонить · ${contact.name} (${contact.login})` : `Позвонить · ${contact.name}`}>
                      Позвонить
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
          <div className="colleagues-actions">
            <Link className="btn btn-secondary" to="/">На главную</Link>
            <button className="btn btn-secondary" disabled={contacts === null && !error} onClick={refresh}>
              Обновить список
            </button>
          </div>
        </section>
      </div>
    </main>
  );
}

type OutgoingState =
  | { step: "ready" }
  | { step: "dialing" }
  | { step: "waiting"; call: DirectCall; deadline: number }
  | { step: "active"; conn: JoinResponse }
  | { step: "finished"; title: string; error?: string };

function OutgoingCall({ colleague, onBack }: { colleague: CallContact; onBack: () => void }) {
  const heading = useRef<HTMLHeadingElement>(null);
  const [state, setState] = useState<OutgoingState>({ step: "ready" });
  const [connecting, setConnecting] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [cancelError, setCancelError] = useState("");
  const pending = useRef<{ id: string; action: "cancel" | "end" } | null>(null);
  const step = state.step;

  useEffect(() => {
    if (step === "ready" || step === "finished") heading.current?.focus({ preventScroll: true });
  }, [step]);

  useEffect(() => {
    const close = () => {
      if (!pending.current) return;
      const { id, action } = pending.current;
      pending.current = null;
      void closeDirectCall(id, action, true).catch(() => {});
    };
    window.addEventListener("pagehide", close);
    return () => {
      window.removeEventListener("pagehide", close);
      close();
    };
  }, []);

  useEffect(() => {
    if (state.step !== "waiting" || cancelling) return;
    let stopped = false;
    let timer: number;
    const { call, deadline } = state;

    async function watch(latest?: DirectCall) {
      let checkingStatus = latest === undefined;
      try {
        const current = latest ?? await getDirectCall(call.id);
        checkingStatus = false;
        if (stopped || pending.current?.id !== call.id) return;
        if (current.status === "ringing") {
          timer = window.setTimeout(() => void watch(), 1_250);
        } else if (current.status === "accepted") {
          setConnecting(true);
          const conn = await connectDirectCall(call.id);
          if (stopped || pending.current?.id !== call.id) return;
          pending.current = { id: call.id, action: "end" };
          setState({ step: "active", conn });
        } else {
          pending.current = null;
          setState({ step: "finished", title: CALL_RESULTS[current.status] });
        }
      } catch (error) {
        if (stopped) return;
        if (checkingStatus && error instanceof ApiError && error.code === "network" && performance.now() < deadline) {
          timer = window.setTimeout(() => void watch(), 1_250);
          return;
        }
        void closeDirectCall(call.id, "cancel", true).catch(() => {});
        setState({ step: "finished", title: "Не удалось продолжить звонок",
          error: callError(error, "Вернитесь к списку коллег и попробуйте ещё раз.") });
      }
    }

    if (call.status === "ringing") timer = window.setTimeout(() => void watch(), 1_250);
    else void watch(call);
    return () => {
      stopped = true;
      window.clearTimeout(timer);
    };
  }, [state, cancelling]);

  const dial = async () => {
    if (pending.current) return;
    const id = crypto.randomUUID();
    pending.current = { id, action: "cancel" };
    setState({ step: "dialing" });
    try {
      const call = await dialColleague(id, colleague.login);
      if (pending.current?.id !== id) {
        if (call.status === "ringing" || call.status === "accepted") {
          void closeDirectCall(id, "cancel", true).catch(() => {});
        }
        return;
      }
      setState({ step: "waiting", call, deadline: performance.now() + call.remaining_ms + 2_000 });
    } catch (error) {
      if (pending.current?.id !== id) return;
      if (error instanceof ApiError && error.code === "network") {
        void closeDirectCall(id, "cancel", true).catch(() => {});
      } else {
        pending.current = null;
      }
      setState({ step: "finished", title: "Не удалось позвонить",
        error: callError(error, "Вернитесь к списку коллег и попробуйте ещё раз.") });
    }
  };

  const cancel = async () => {
    if (!pending.current || cancelling) return;
    const { id } = pending.current;
    setCancelling(true);
    setCancelError("");
    try {
      const call = await closeDirectCall(id, "cancel");
      if (call.status === "ringing" || call.status === "accepted") throw new Error("Call is still active");
      pending.current = null;
      setState({ step: "finished", title: CALL_RESULTS[call.status] });
    } catch (error) {
      if (error instanceof ApiError && error.code === "call_not_found") {
        pending.current = null;
        setState({ step: "finished", title: CALL_RESULTS.ended });
      } else {
        setCancelError(callError(error, "Не удалось отменить вызов. Попробуйте ещё раз."));
      }
    } finally {
      setCancelling(false);
    }
  };

  const leave = (reason: LeaveReason) => {
    if (!pending.current) return;
    const { id } = pending.current;
    pending.current = null;
    void closeDirectCall(id, "end", true).catch(() => {});
    setState(reason === "lost"
      ? { step: "finished", title: "Связь прервалась", error: "Проверьте интернет и позвоните ещё раз." }
      : { step: "finished", title: CALL_RESULTS.ended });
  };

  if (state.step === "active") {
    return <CallRoom conn={state.conn} colleague={colleague.name} onLeave={leave}
      choices={{ name: state.conn.name, micOn: true, camOn: false }} />;
  }

  const finished = state.step === "finished";
  const status = cancelling ? "Отменяем вызов…" : connecting ? "Подключаемся…"
    : state.step === "dialing" ? "Отправляем вызов…" : "Ждём ответа…";
  return (
    <main className="page">
      <header className="topbar"><Logo /></header>
      <div className="message-wrap">
        <section className="panel message screen-enter">
          <span className="message-mark" aria-hidden="true"><UsersIcon /></span>
          <h1 className="message-title" ref={heading} tabIndex={-1}>{colleague.name}</h1>
          {state.step === "ready" && <p className="message-text">Вызов поступит в SelfCall на телефон коллеги.</p>}
          {state.step !== "ready" && <p className="message-text" role="status">{finished ? state.title : status}</p>}
          {finished && state.error && <p className="field-error" role="alert">{state.error}</p>}
          {!finished && cancelError && <p className="field-error" role="alert">{cancelError}</p>}
          <div className="message-actions">
            {state.step === "ready" && <button className="btn btn-primary" onClick={dial}>Позвонить</button>}
            {state.step === "ready" || finished
              ? <button className="btn btn-secondary" onClick={onBack}>К списку коллег</button>
              : <button className="btn btn-secondary" onClick={cancel} disabled={state.step === "dialing" || cancelling}>
                  Отменить вызов
                </button>}
          </div>
        </section>
      </div>
    </main>
  );
}
