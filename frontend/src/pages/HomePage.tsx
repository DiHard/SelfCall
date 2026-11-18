import { useRef, useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ApiError, createMeeting } from "../api";
import { ArrowRightIcon, CamIcon, LinkIcon, PlusIcon, UsersIcon } from "../icons";
import { parseMeetingId } from "../lib";
import { Logo } from "../components/Logo";
import { useFocusHeading } from "../components/useFocusHeading";

const CREATE_ERRORS: Record<string, string> = {
  server_busy: "Сейчас на сервере идёт максимум встреч. Попробуйте через несколько минут.",
  too_many_requests: "С этого устройства создано слишком много встреч. Попробуйте через час.",
  network: "Нет связи с сервером. Проверьте интернет и попробуйте ещё раз.",
};

export function HomePage() {
  const navigate = useNavigate();
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const [code, setCode] = useState("");
  const [codeError, setCodeError] = useState<string | null>(null);
  const codeInput = useRef<HTMLInputElement>(null);
  const heading = useFocusHeading<HTMLHeadingElement>();

  const create = async () => {
    setCreating(true);
    setCreateError(null);
    try {
      const { id } = await createMeeting();
      navigate(`/m/${id}`, { state: { created: true } });
    } catch (e) {
      const c = e instanceof ApiError ? e.code : "unknown";
      setCreateError(CREATE_ERRORS[c] ?? "Не получилось создать встречу. Попробуйте ещё раз.");
      setCreating(false);
    }
  };

  const joinByCode = (e: FormEvent) => {
    e.preventDefault();
    const id = parseMeetingId(code);
    if (!id) {
      setCodeError(
        code.trim()
          ? "Не похоже на ссылку встречи. Код выглядит так: abc-defg-hjk"
          : "Вставьте ссылку на встречу или её код",
      );
      codeInput.current?.focus();
      return;
    }
    navigate(`/m/${id}`);
  };

  return (
    <main className="page">
      <header className="topbar"><Logo /></header>
      <div className="home">
        <div className="home-intro screen-enter">
          <h1 className="home-title" ref={heading} tabIndex={-1}>Видеовстречи по&nbsp;ссылке</h1>
          <p className="home-lead">
            Создайте встречу и&nbsp;отправьте ссылку. Собеседник сможет войти прямо из браузера.
          </p>
          <div className="home-art" aria-hidden="true">
            <span className="home-art-frame"><CamIcon /></span>
            <span className="home-art-frame"><UsersIcon /></span>
            <span className="home-art-link"><LinkIcon /></span>
          </div>
        </div>

        <div className="panel home-actions screen-enter">
          <button className="btn btn-primary btn-lg home-create" onClick={create} disabled={creating} aria-busy={creating}>
            {creating ? <span className="busy-mark" aria-hidden="true" /> : <PlusIcon />}
            {creating ? "Создаём встречу…" : "Создать встречу"}
          </button>
          {createError && <p className="field-error" role="alert">{createError}</p>}

          <form className="join-form" onSubmit={joinByCode} noValidate>
            <label className="field-label" htmlFor="code">Ссылка или код встречи</label>
            <div className="join-fields">
              <input
                id="code"
                ref={codeInput}
                className="input"
                type="text"
                name="meeting-code"
                inputMode="url"
                autoComplete="off"
                autoCapitalize="none"
                spellCheck={false}
                placeholder="abc-defg-hjk"
                value={code}
                onChange={(e) => {
                  setCode(e.target.value);
                  setCodeError(null);
                }}
                aria-invalid={!!codeError}
                aria-describedby={codeError ? "code-error" : undefined}
              />
              <button className="btn btn-secondary" type="submit">
                Войти <ArrowRightIcon />
              </button>
            </div>
            {codeError && <p id="code-error" className="field-error" role="alert">{codeError}</p>}
          </form>
          <Link className="btn btn-secondary home-calls" to="/calls">
            <UsersIcon /> Звонки коллегам
          </Link>
        </div>
      </div>
    </main>
  );
}
