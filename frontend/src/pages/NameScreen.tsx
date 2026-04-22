import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { getUsername, setUsername } from "../username";

export function NameScreen() {
  const [name, setName] = useState(getUsername());
  const navigate = useNavigate();

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = name.trim();
    if (!trimmed) return;
    setUsername(trimmed);
    navigate("/rooms");
  };

  return (
    <div className="screen">
      <h1>SelfCall</h1>
      <p className="muted">Введите имя, под которым вас будут видеть.</p>
      <form onSubmit={submit} className="form">
        <input
          autoFocus
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Ваше имя"
          maxLength={64}
        />
        <button type="submit" disabled={!name.trim()}>
          Продолжить
        </button>
      </form>
    </div>
  );
}
