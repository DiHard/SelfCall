import { useCallback, useEffect, useState } from "react";
import { Link, useLocation, useParams } from "react-router-dom";
import { ApiError, getMeeting, joinMeeting, type JoinResponse, type MeetingInfo } from "../api";
import { CallRoom, type LeaveReason } from "../components/CallRoom";
import { Logo } from "../components/Logo";
import { PreJoin, type JoinChoices } from "../components/PreJoin";
import { parseMeetingId } from "../lib";
import { useFocusHeading } from "../components/useFocusHeading";

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

export function MeetingPage() {
  const { meetingId: rawId = "" } = useParams();
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

  switch (state.step) {
    case "checking":
      return (
        <main className="page page-center" aria-busy="true">
          <p className="muted">Проверяем ссылку…</p>
        </main>
      );

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
        <Message title={state.title} text={state.text}>
          {state.retry && (
            <button className="btn btn-primary" onClick={check}>
              Попробовать снова
            </button>
          )}
          <Link className="btn btn-secondary" to="/">
            На главную
          </Link>
        </Message>
      );
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
    <Message title={title} text={text}>
      <button className="btn btn-primary" onClick={onRejoin}>
        Вернуться во встречу
      </button>
      <Link className="btn btn-secondary" to="/">
        На главную
      </Link>
    </Message>
  );
}

function Message({ title, text, children }: { title: string; text: string; children: React.ReactNode }) {
  const heading = useFocusHeading<HTMLHeadingElement>();
  return (
    <main className="page page-center">
      <div className="message">
        <Logo link />
        <h1 className="message-title" ref={heading} tabIndex={-1}>
          {title}
        </h1>
        <p className="message-text">{text}</p>
        <div className="message-actions">{children}</div>
      </div>
    </main>
  );
}
