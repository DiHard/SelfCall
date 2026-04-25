import { useEffect, useState } from "react";
import { useNavigate, useParams, useLocation } from "react-router-dom";
import {
  LiveKitRoom,
  GridLayout,
  ParticipantTile,
  useTracks,
  useLocalParticipant,
  useRoomContext,
  RoomAudioRenderer,
} from "@livekit/components-react";
import "@livekit/components-styles";
import { Track } from "livekit-client";
import { fetchToken, type TokenResponse } from "../api";
import { getUsername } from "../username";

export function CallScreen() {
  const { roomName = "" } = useParams<{ roomName: string }>();
  const location = useLocation();
  const displayName: string = (location.state as { display?: string } | null)?.display ?? roomName;
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
        <p className="muted">Подключение к «{displayName}»…</p>
      </div>
    );
  }

  return (
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
      <VideoRoom />
    </LiveKitRoom>
  );
}

function VideoRoom() {
  const room = useRoomContext();
  const {
    localParticipant,
    isMicrophoneEnabled,
    isCameraEnabled,
    isScreenShareEnabled,
  } = useLocalParticipant();

  const tracks = useTracks(
    [
      { source: Track.Source.Camera, withPlaceholder: true },
      { source: Track.Source.ScreenShare, withPlaceholder: false },
    ],
    { onlySubscribed: false },
  );

  return (
    <div className="call-layout">
      <div className="call-videos">
        <GridLayout tracks={tracks} style={{ height: "100%" }}>
          <ParticipantTile />
        </GridLayout>
      </div>

      <div className="ctrl-bar">
        <button
          className={isMicrophoneEnabled ? "" : "off"}
          onClick={() =>
            localParticipant.setMicrophoneEnabled(!isMicrophoneEnabled)
          }
        >
          {isMicrophoneEnabled ? "🎙 Выключить микрофон" : "🔇 Включить микрофон"}
        </button>

        <button
          className={isCameraEnabled ? "" : "off"}
          onClick={() =>
            localParticipant.setCameraEnabled(!isCameraEnabled)
          }
        >
          {isCameraEnabled ? "📷 Выключить камеру" : "📷 Включить камеру"}
        </button>

        <button
          className={isScreenShareEnabled ? "active" : ""}
          onClick={() =>
            localParticipant
              .setScreenShareEnabled(!isScreenShareEnabled)
              .catch(() => {})
          }
        >
          {isScreenShareEnabled ? "🖥 Остановить показ" : "🖥 Экран"}
        </button>

        <button className="leave" onClick={() => room.disconnect()}>
          📞 Завершить
        </button>
      </div>

      <RoomAudioRenderer />
    </div>
  );
}
