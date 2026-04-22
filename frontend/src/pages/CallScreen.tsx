import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { LiveKitRoom, VideoConference } from "@livekit/components-react";
import "@livekit/components-styles";
import { fetchToken, type TokenResponse } from "../api";
import { getUsername } from "../username";

export function CallScreen() {
  const { roomName = "" } = useParams<{ roomName: string }>();
  const navigate = useNavigate();
  const username = getUsername();

  const [conn, setConn] = useState<TokenResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!username) {
      navigate("/");
      return;
    }
    fetchToken(roomName, username)
      .then(setConn)
      .catch((e) => setError(String(e)));
  }, [roomName, username, navigate]);

  if (error) {
    return (
      <div className="screen">
        <p className="error">{error}</p>
        <button onClick={() => navigate("/rooms")}>← Назад</button>
      </div>
    );
  }

  if (!conn) {
    return (
      <div className="screen">
        <p className="muted">Подключение к «{roomName}»…</p>
      </div>
    );
  }

  return (
    <div className="call-container">
      <LiveKitRoom
        token={conn.token}
        serverUrl={conn.url}
        connect={true}
        video={true}
        audio={true}
        onDisconnected={() => navigate("/rooms")}
        data-lk-theme="default"
        style={{ height: "100vh" }}
      >
        <VideoConference />
      </LiveKitRoom>
    </div>
  );
}
