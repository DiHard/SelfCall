import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { fetchRooms, type Room } from "../api";
import { clearUsername, getUsername } from "../username";

export function RoomsScreen() {
  const navigate = useNavigate();
  const username = getUsername();
  const [rooms, setRooms] = useState<Room[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!username) {
      navigate("/");
      return;
    }
    fetchRooms()
      .then(setRooms)
      .catch((e) => setError(String(e)));
  }, [username, navigate]);

  const logout = () => {
    clearUsername();
    navigate("/");
  };

  return (
    <div className="screen">
      <header className="header">
        <h1>Комнаты</h1>
        <div>
          <span className="muted">{username}</span>{" "}
          <button className="link" onClick={logout}>
            сменить имя
          </button>
        </div>
      </header>

      {error && <p className="error">{error}</p>}
      {!rooms && !error && <p className="muted">Загрузка…</p>}
      {rooms && (
        <ul className="rooms">
          {rooms.map((r) => (
            <li key={r.name}>
              <button onClick={() => navigate(`/room/${r.name}`, { state: { display: r.display } })}>
                {r.display}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
