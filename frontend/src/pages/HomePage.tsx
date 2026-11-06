import { useRef, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { ApiError, createMeeting } from "../api";
import { ArrowRightIcon, PlusIcon } from "../icons";
import { parseMeetingId } from "../lib";
import { Logo } from "../components/Logo";

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
    <main className="page page-center">
      <div className="home">
        <Logo />
        <h1 className="home-title">Видеовстречи по&nbsp;ссылке</h1>
        <p className="home-lead">
          Создайте встречу и&nbsp;отправьте ссылку. Собеседнику не&nbsp;нужно
          регистрироваться или что-то устанавливать.
        </p>

        <button className="btn btn-primary btn-lg home-create" onClick={create} disabled={creating}>
          <PlusIcon />
          {creating ? "Создаём встречу…" : "Создать встречу"}
        </button>
        {createError && (
          <p className="field-error" role="alert">
            {createError}
          </p>
        )}

        <div className="divider">
          <label htmlFor="code">или войдите по ссылке или коду</label>
        </div>

        <form className="join-form" onSubmit={joinByCode} noValidate>
          <input
            id="code"
            ref={codeInput}
            className="input"
            type="text"
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
            Войти
            <ArrowRightIcon />
          </button>
        </form>
        {codeError && (
          <p id="code-error" className="field-error" role="alert">
            {codeError}
          </p>
        )}
      </div>
    </main>
  );
}
