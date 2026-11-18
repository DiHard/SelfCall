import { useCallback, useEffect, useState } from "react";
import { Link, useLocation, useParams } from "react-router-dom";
import { ApiError, getMeeting, joinMeeting, type JoinResponse, type MeetingInfo } from "../api";
import { CallRoom, type LeaveReason } from "../components/CallRoom";
import { Logo } from "../components/Logo";
import { PreJoin, type JoinChoices } from "../components/PreJoin";
import { ToastProvider } from "../components/Toast";
import { parseMeetingId } from "../lib";
import { useFocusHeading } from "../components/useFocusHeading";
import { AlertIcon, CheckIcon, LinkIcon } from "../icons";

type State =
  | { step: "checking" }
  | { step: "prejoin"; info: MeetingInfo; joinError?: string }
  | { step: "joining"; info: MeetingInfo }
  | { step: "call"; conn: JoinResponse; choices: JoinChoices }
  | { step: "left"; reason: LeaveReason }
  | { step: "error"; title: string; text: string; retry: boolean };

const JOIN_ERRORS: Record<string, string> = {
  meeting_full: "Во встрече нет свободных мест. Попробуйте войти, когда кто-нибудь выйдет.",
  server_busy: "Сейчас на сервере идёт максимум встреч. Попробуйте через несколько минут.",
  too_many_requests: "Слишком много попыток входа. Подождите минуту.",
  network: "Нет связи с сервером. Проверьте интернет и попробуйте ещё раз.",
};

export function MeetingPage({ dismissUpdate }: { dismissUpdate?: () => void }) {
  const { meetingId = "" } = useParams();
  return <MeetingFlow key={meetingId} rawId={meetingId} dismissUpdate={dismissUpdate} />;
}

function MeetingFlow({ rawId, dismissUpdate }: { rawId: string; dismissUpdate?: () => void }) {
  const meetingId = parseMeetingId(rawId);
  const justCreated = !!(useLocation().state as { created?: boolean } | null)?.created;
  const [state, setState] = useState<State>(() => (meetingId ? { step: "checking" } : notFound()));
  const [choices, setChoices] = useState<JoinChoices | null>(null);

  const load = useCallback(() => {
    if (meetingId) fetchMeetingState(meetingId).then(setState);
  }, [meetingId]);

  useEffect(load, [load]);

  const check = () => {
    setState({ step: "checking" });
    load();
  };

  const join = async (info: MeetingInfo, c: JoinChoices) => {
    setChoices(c);
    setState({ step: "joining", info });
    try {
      const conn = await joinMeeting(info.id, c.name);
      setState({ step: "call", conn, choices: c });
    } catch (e) {
      const code = e instanceof ApiError ? e.code : "unknown";
      setState({
        step: "prejoin",
        info,
        joinError: JOIN_ERRORS[code] ?? "Не получилось войти. Попробуйте ещё раз.",
      });
    }
  };

  // Keep the tab title useful when several tabs are open
  useEffect(() => {
    document.title = state.step === "call" ? `Встреча ${meetingId} — SelfCall` : "SelfCall — видеовстречи";
  }, [state.step, meetingId]);

  const showUpdate = state.step !== "joining" && state.step !== "call";
  return <ToastProvider dismissUpdate={showUpdate ? dismissUpdate : undefined}>{renderScreen()}</ToastProvider>;

  function renderScreen() {
    switch (state.step) {
      case "checking":
        return <Message tone="checking" title="Проверяем ссылку…" />;

      case "prejoin":
      case "joining":
        return (
          <PreJoin
            info={state.info}
            justCreated={justCreated}
            joining={state.step === "joining"}
            error={state.step === "prejoin" ? state.joinError : undefined}
            initial={choices}
            onJoin={(c) => join(state.info, c)}
          />
        );

      case "call":
        return (
          <CallRoom
            conn={state.conn}
            choices={state.choices}
            onLeave={(reason) => setState({ step: "left", reason })}
          />
        );

      case "left":
        return <LeftScreen reason={state.reason} onRejoin={check} />;

      case "error":
        return (
          <Message tone="error" title={state.title} text={state.text}>
            {state.retry && (
              <button className="btn btn-primary" onClick={check}>
                Попробовать снова
              </button>
            )}
            <Link className={`btn ${state.retry ? "btn-secondary" : "btn-primary"}`} to="/">
              На главную
            </Link>
          </Message>
        );
    }
  }
}

async function fetchMeetingState(meetingId: string): Promise<State> {
  try {
    return { step: "prejoin", info: await getMeeting(meetingId) };
  } catch (e) {
    if (e instanceof ApiError && e.code === "meeting_not_found") return notFound();
    return {
      step: "error",
      title: "Нет связи с сервером",
      text: "Проверьте интернет и попробуйте ещё раз.",
      retry: true,
    };
  }
}

function notFound(): State {
  return {
    step: "error",
    title: "Встреча не найдена",
    text: "Проверьте ссылку: возможно, в ней опечатка или она скопирована не полностью.",
    retry: false,
  };
}

const LEFT_TEXT: Record<LeaveReason, { title: string; text: string }> = {
  left: { title: "Вы вышли из встречи", text: "Ссылка продолжает работать — можно вернуться в любой момент." },
  ended: {
    title: "Встреча завершена",
    text: "Встреча шла дольше разрешённого времени. Чтобы продолжить, войдите снова по той же ссылке.",
  },
  lost: { title: "Связь прервалась", text: "Проверьте интернет и вернитесь во встречу." },
};

function LeftScreen({ reason, onRejoin }: { reason: LeaveReason; onRejoin: () => void }) {
  const { title, text } = LEFT_TEXT[reason];
  return (
    <Message tone={reason === "lost" ? "error" : "complete"} title={title} text={text}>
      <button className="btn btn-primary" onClick={onRejoin}>
        Вернуться во встречу
      </button>
      <Link className="btn btn-secondary" to="/">
        На главную
      </Link>
    </Message>
  );
}

function Message({ tone, title, text, children }: {
  tone: "checking" | "complete" | "error";
  title: string;
  text?: string;
  children?: React.ReactNode;
}) {
  const heading = useFocusHeading<HTMLHeadingElement>();
  const Icon = tone === "checking" ? LinkIcon : tone === "complete" ? CheckIcon : AlertIcon;
  return (
    <main className="page">
      <header className="topbar"><Logo link /></header>
      <div className="message-wrap">
        <section className={`panel message message-${tone} screen-enter`}>
          <span className="message-mark"><Icon /></span>
          <h1 className="message-title" ref={heading} tabIndex={-1}>{title}</h1>
          {text && <p className="message-text">{text}</p>}
          {tone === "checking" && <div className="progress-track" role="progressbar" aria-label="Проверка ссылки" />}
          {children && <div className="message-actions">{children}</div>}
        </section>
      </div>
    </main>
  );
}
