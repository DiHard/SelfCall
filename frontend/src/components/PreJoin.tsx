import { useEffect, useRef, useState, type FormEvent } from "react";
import {
  createLocalAudioTrack,
  createLocalVideoTrack,
  type LocalAudioTrack,
  type LocalVideoTrack,
} from "livekit-client";
import type { MeetingInfo } from "../api";
import { CamIcon, CamOffIcon, MicIcon, MicOffIcon } from "../icons";
import { loadPrefs, plural, savePrefs } from "../lib";
import { LinkBox } from "./LinkBox";
import { useFocusHeading } from "./useFocusHeading";
import { Logo } from "./Logo";

export type JoinChoices = {
  name: string;
  micOn: boolean;
  camOn: boolean;
  micId?: string;
  camId?: string;
};

type MediaProblem = "denied" | "missing" | "busy" | null;

const PROBLEM_TEXT: Record<Exclude<MediaProblem, null>, { cam: string; mic: string }> = {
  denied: {
    cam: "Браузер не дал доступ к камере. Разрешите его в настройках сайта (значок слева от адреса).",
    mic: "Браузер не дал доступ к микрофону. Разрешите его в настройках сайта (значок слева от адреса).",
  },
  missing: {
    cam: "Камера не найдена. Подключите её или войдите без камеры.",
    mic: "Микрофон не найден. Подключите его или войдите без микрофона.",
  },
  busy: {
    cam: "Камера занята другой программой. Закройте её и нажмите на кнопку камеры.",
    mic: "Микрофон занят другой программой. Закройте её и нажмите на кнопку микрофона.",
  },
};

function problemOf(e: unknown): MediaProblem {
  const name = (e as { name?: string })?.name;
  if (name === "NotAllowedError" || name === "SecurityError") return "denied";
  if (name === "NotFoundError" || name === "OverconstrainedError") return "missing";
  return "busy";
}

type Props = {
  info: MeetingInfo;
  justCreated: boolean;
  joining: boolean;
  error?: string;
  initial: JoinChoices | null;
  onJoin: (c: JoinChoices) => void;
};

export function PreJoin({ info, justCreated, joining, error, initial, onJoin }: Props) {
  const saved = initial ?? loadPrefs();
  const [name, setName] = useState(saved.name);
  const [camOn, setCamOn] = useState(saved.camOn);
  const [micOn, setMicOn] = useState(saved.micOn);
  const [camId, setCamId] = useState(saved.camId);
  const [micId, setMicId] = useState(saved.micId);
  const [camProblem, setCamProblem] = useState<MediaProblem>(null);
  const [micProblem, setMicProblem] = useState<MediaProblem>(null);
  const [devices, setDevices] = useState<MediaDeviceInfo[]>([]);
  const [videoTrack, setVideoTrack] = useState<LocalVideoTrack | null>(null);
  const [audioTrack, setAudioTrack] = useState<LocalAudioTrack | null>(null);
  // Bumped to ask for a device again after the browser refused it
  const [camAttempt, setCamAttempt] = useState(0);
  const [micAttempt, setMicAttempt] = useState(0);
  const videoEl = useRef<HTMLVideoElement>(null);
  const heading = useFocusHeading<HTMLHeadingElement>();

  // What the person actually gets: a refused device counts as off
  const camLive = camOn && !camProblem;
  const micLive = micOn && !micProblem;

  const toggleCam = () => {
    if (camProblem) {
      setCamProblem(null);
      setCamOn(true);
      setCamAttempt((n) => n + 1);
    } else setCamOn(!camOn);
  };
  const toggleMic = () => {
    if (micProblem) {
      setMicProblem(null);
      setMicOn(true);
      setMicAttempt((n) => n + 1);
    } else setMicOn(!micOn);
  };

  // ----- Camera preview -----
  useEffect(() => {
    if (!camOn) return;
    let track: LocalVideoTrack | null = null;
    let cancelled = false;
    createLocalVideoTrack({ deviceId: camId, resolution: { width: 1280, height: 720 } })
      .then((t) => {
        if (cancelled) return t.stop();
        track = t;
        setCamProblem(null);
        setVideoTrack(t);
      })
      .catch((e) => !cancelled && setCamProblem(problemOf(e)));
    return () => {
      cancelled = true;
      track?.stop();
      setVideoTrack(null);
    };
  }, [camOn, camId, camAttempt]);

  useEffect(() => {
    if (videoTrack && videoEl.current) videoTrack.attach(videoEl.current);
    return () => {
      videoTrack?.detach();
    };
  }, [videoTrack]);

  // ----- Microphone check -----
  useEffect(() => {
    if (!micOn) return;
    let track: LocalAudioTrack | null = null;
    let cancelled = false;
    createLocalAudioTrack({ deviceId: micId })
      .then((t) => {
        if (cancelled) return t.stop();
        track = t;
        setMicProblem(null);
        setAudioTrack(t);
      })
      .catch((e) => !cancelled && setMicProblem(problemOf(e)));
    return () => {
      cancelled = true;
      track?.stop();
      setAudioTrack(null);
    };
  }, [micOn, micId, micAttempt]);

  // ----- Device lists (labels appear only after permission is granted) -----
  useEffect(() => {
    const md = navigator.mediaDevices;
    if (!md?.enumerateDevices) return;
    const update = () => md.enumerateDevices().then((list) => setDevices(list.filter((d) => d.deviceId)));
    update();
    md.addEventListener("devicechange", update);
    return () => md.removeEventListener("devicechange", update);
  }, [videoTrack, audioTrack]);

  const cams = devices.filter((d) => d.kind === "videoinput");
  const mics = devices.filter((d) => d.kind === "audioinput");
  const activeCamId = videoTrack?.mediaStreamTrack.getSettings().deviceId ?? camId;
  const activeMicId = audioTrack?.mediaStreamTrack.getSettings().deviceId ?? micId;

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const choices: JoinChoices = {
      name: name.trim(),
      // Don't ask LiveKit for a device the browser refused — join without it instead
      camOn: camLive,
      micOn: micLive,
      camId: activeCamId,
      micId: activeMicId,
    };
    savePrefs({ name: choices.name, camOn, micOn, camId: activeCamId, micId: activeMicId });
    // Free the devices before the call grabs them (matters on phones)
    videoTrack?.stop();
    audioTrack?.stop();
    onJoin(choices);
  };

  const others = info.participants;
  const presence =
    others === 0
      ? "Во встрече пока никого нет"
      : `Во встрече ${others} ${plural(others, ["человек", "человека", "человек"])}`;

  return (
    <main className="page prejoin-page">
      <header className="topbar">
        <Logo link />
      </header>

      <div className="prejoin">
        <section className="preview" aria-label="Предпросмотр камеры">
          <div className="preview-frame">
            {camOn && videoTrack ? (
              <video ref={videoEl} className="preview-video" muted playsInline autoPlay />
            ) : (
              <div className="preview-off">
                {camOn && camProblem
                  ? PROBLEM_TEXT[camProblem].cam
                  : camOn
                    ? "Включаем камеру…"
                    : "Камера выключена"}
              </div>
            )}
            <div className="preview-controls">
              <button
                type="button"
                className={`round-btn ${micLive ? "" : "round-btn-off"}`}
                onClick={toggleMic}
                aria-pressed={micLive}
                aria-label="Микрофон"
                title={micLive ? "Выключить микрофон" : "Включить микрофон"}
              >
                {micLive ? <MicIcon /> : <MicOffIcon />}
              </button>
              <button
                type="button"
                className={`round-btn ${camLive ? "" : "round-btn-off"}`}
                onClick={toggleCam}
                aria-pressed={camLive}
                aria-label="Камера"
                title={camLive ? "Выключить камеру" : "Включить камеру"}
              >
                {camLive ? <CamIcon /> : <CamOffIcon />}
              </button>
            </div>
            {micLive && audioTrack && <MicLevel track={audioTrack} />}
          </div>

          {micOn && micProblem && (
            <p className="field-error" role="alert">
              {PROBLEM_TEXT[micProblem].mic}
            </p>
          )}

          {(cams.length > 1 || mics.length > 1) && (
            <div className="device-selects">
              {mics.length > 1 && (
                <DeviceSelect label="Микрофон" devices={mics} value={activeMicId} onChange={setMicId} />
              )}
              {cams.length > 1 && (
                <DeviceSelect label="Камера" devices={cams} value={activeCamId} onChange={setCamId} />
              )}
            </div>
          )}
        </section>

        <section className="prejoin-side">
          <h1 className="prejoin-title" ref={heading} tabIndex={-1}>{justCreated ? "Встреча создана" : "Готовы войти?"}</h1>
          <p className="muted">{justCreated ? "Отправьте ссылку тем, с кем хотите поговорить." : presence}</p>

          <LinkBox meetingId={info.id} />

          <form className="prejoin-form" onSubmit={submit}>
            <label className="field-label" htmlFor="name">
              Ваше имя <span className="muted">— необязательно</span>
            </label>
            <input
              id="name"
              className="input"
              type="text"
              autoComplete="name"
              maxLength={40}
              placeholder="Гость"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
            <button className="btn btn-primary btn-lg" type="submit" disabled={joining}>
              {joining ? "Входим…" : "Войти во встречу"}
            </button>
            {error && (
              <p className="field-error" role="alert">
                {error}
              </p>
            )}
          </form>
        </section>
      </div>
    </main>
  );
}

function DeviceSelect(props: {
  label: string;
  devices: MediaDeviceInfo[];
  value?: string;
  onChange: (id: string) => void;
}) {
  return (
    <label className="device-select">
      <span className="field-label">{props.label}</span>
      <select className="input" value={props.value} onChange={(e) => props.onChange(e.target.value)}>
        {props.devices.map((d, i) => (
          <option key={d.deviceId} value={d.deviceId}>
            {d.label || `${props.label} ${i + 1}`}
          </option>
        ))}
      </select>
    </label>
  );
}

/** Five bars that move with the microphone volume, so people see the mic works. */
function MicLevel({ track }: { track: LocalAudioTrack }) {
  const [level, setLevel] = useState(0);
  useEffect(() => {
    const ctx = new AudioContext();
    const source = ctx.createMediaStreamSource(new MediaStream([track.mediaStreamTrack]));
    const analyser = ctx.createAnalyser();
    analyser.fftSize = 512;
    source.connect(analyser);
    const data = new Uint8Array(analyser.fftSize);
    let raf = 0;
    const tick = () => {
      analyser.getByteTimeDomainData(data);
      let sum = 0;
      for (const v of data) sum += ((v - 128) / 128) ** 2;
      setLevel(Math.min(1, Math.sqrt(sum / data.length) * 4));
      raf = requestAnimationFrame(tick);
    };
    tick();
    return () => {
      cancelAnimationFrame(raf);
      ctx.close();
    };
  }, [track]);

  const bars = Math.round(level * 5);
  return (
    <div className="mic-level" aria-hidden="true">
      {[0, 1, 2, 3, 4].map((i) => (
        <span key={i} className={i < bars ? "on" : ""} />
      ))}
    </div>
  );
}
