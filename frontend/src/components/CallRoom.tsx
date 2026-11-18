import { useCallback, useEffect, useId, useRef, useState } from "react";
import {
  CarouselLayout,
  FocusLayoutContainer,
  GridLayout,
  LayoutContextProvider,
  LiveKitRoom,
  ParticipantTile,
  RoomAudioRenderer,
  StartAudio,
  useConnectionState,
  useCreateLayoutContext,
  useEnsureTrackRef,
  useMediaDeviceSelect,
  usePinnedTracks,
  useParticipantInfo,
  useParticipants,
  useRoomContext,
  isTrackReference,
  useLayoutContext,
  useTrackToggle,
  useTracks,
  type TrackReferenceOrPlaceholder,
} from "@livekit/components-react";
import "@livekit/components-styles";
import { ConnectionState, DisconnectReason, RoomEvent, ScreenSharePresets, Track, type RoomOptions } from "livekit-client";
import type { JoinResponse } from "../api";
import {
  CamIcon,
  CamOffIcon,
  CheckIcon,
  ChevronUpIcon,
  CopyIcon,
  HangUpIcon,
  LinkIcon,
  MicIcon,
  MicOffIcon,
  ScreenIcon,
  ScreenStopIcon,
  ShareIcon,
  UsersIcon,
} from "../icons";
import { canShareScreen, meetingUrl, plural, shareLink } from "../lib";
import { Logo } from "./Logo";
import type { JoinChoices } from "./PreJoin";
import { useCopyFeedback } from "./useCopyFeedback";
import { useFocusHeading } from "./useFocusHeading";
import { useToast } from "./toast-context";

export type LeaveReason = "left" | "ended" | "lost";

const ROOM_OPTIONS: RoomOptions = {
  adaptiveStream: true,
  dynacast: true,
  // The SDK also disconnects on beforeunload, even if the user stays.
  disconnectOnPageLeave: false,
  publishDefaults: {
    // Sharp text on shared screens matters more than smooth motion
    screenShareEncoding: ScreenSharePresets.h1080fps15.encoding,
  },
};

type Props = {
  conn: JoinResponse;
  choices: JoinChoices;
  colleague?: string;
  onLeave: (reason: LeaveReason) => void;
};

export function CallRoom({ conn, choices, colleague, onLeave }: Props) {
  const toast = useToast();
  const [startedAt] = useState(() =>
    conn.elapsed_ms == null ? null : performance.now() - conn.elapsed_ms,
  );
  // Created once: new objects on every render would make LiveKit re-acquire devices
  const [audio] = useState(() => (choices.micOn ? { deviceId: choices.micId } : false));
  const [video] = useState(() =>
    choices.camOn ? { deviceId: choices.camId, resolution: { width: 1280, height: 720 } } : false,
  );

  const handleDisconnect = (reason?: DisconnectReason) => {
    if (reason === DisconnectReason.CLIENT_INITIATED) onLeave("left");
    else if (reason === DisconnectReason.ROOM_DELETED) onLeave("ended");
    else onLeave("lost");
  };

  return (
    <LiveKitRoom
      token={conn.token}
      serverUrl={conn.url}
      connect
      audio={audio}
      video={video}
      options={ROOM_OPTIONS}
      onDisconnected={handleDisconnect}
      onMediaDeviceFailure={(_failure, kind) =>
        toast(
          kind === "videoinput"
            ? "Не получилось включить камеру. Проверьте доступ к ней в настройках браузера."
            : "Не получилось включить микрофон. Проверьте доступ к нему в настройках браузера.",
          "error",
        )
      }
      onError={() => {
        if (colleague !== undefined) onLeave("lost");
        else toast("Не получилось подключиться к встрече. Проверьте интернет и войдите снова.", "error");
      }}
      data-lk-theme="default"
      className="call"
    >
      <CallLayout meetingId={conn.meeting_id} colleague={colleague} startedAt={startedAt} />
      <RoomAudioRenderer />
    </LiveKitRoom>
  );
}

function CallLayout({ meetingId, colleague, startedAt }: {
  meetingId: string;
  colleague?: string;
  startedAt: number | null;
}) {
  const layout = useCreateLayoutContext();
  const room = useRoomContext();
  const connection = useConnectionState(room);
  const inMeeting =
    connection === ConnectionState.Connected ||
    connection === ConnectionState.Reconnecting ||
    connection === ConnectionState.SignalReconnecting;

  useEffect(() => {
    if (!inMeeting) return;

    const confirmPageLeave = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = true;
    };
    const disconnectOnPageHide = () => void room.disconnect();

    window.addEventListener("beforeunload", confirmPageLeave);
    window.addEventListener("pagehide", disconnectOnPageHide);
    return () => {
      window.removeEventListener("beforeunload", confirmPageLeave);
      window.removeEventListener("pagehide", disconnectOnPageHide);
    };
  }, [inMeeting, room]);

  return (
    <LayoutContextProvider value={layout}>
      <main className="call-shell">
        <CallHeader meetingId={meetingId} colleague={colleague} startedAt={inMeeting ? startedAt : null} connection={connection} />
        <Stage />
        <Controls direct={colleague !== undefined} />
      </main>
    </LayoutContextProvider>
  );
}

function CallHeader({ meetingId, colleague, startedAt, connection }: {
  meetingId: string;
  colleague?: string;
  startedAt: number | null;
  connection: ConnectionState;
}) {
  const count = useParticipants().length;
  const heading = useFocusHeading<HTMLHeadingElement>();
  const connected = connection === ConnectionState.Connected;
  const reconnecting = connection === ConnectionState.Reconnecting || connection === ConnectionState.SignalReconnecting;
  return (
    <header className="call-header">
      <div className="call-context">
        <span className="call-brand"><Logo /></span>
        <h1 ref={heading} tabIndex={-1} className="call-title">
          {colleague === undefined ? <>Встреча <span>{meetingId}</span></> : colleague}
        </h1>
      </div>
      <div className="call-stats">
        <span className="call-connection" data-connected={connected} role="status">
          {connected ? "На связи" : reconnecting ? "Восстанавливаем связь…" : "Подключаемся…"}
        </span>
        {startedAt !== null && <CallDuration startedAt={startedAt} />}
        <span className="call-count" title="Участники">
          <UsersIcon />
          <span>
            {count}
            <span className="visually-hidden"> {plural(count, ["участник", "участника", "участников"])}</span>
          </span>
        </span>
      </div>
      {colleague === undefined
        ? <InviteButton meetingId={meetingId} prominent={count <= 1} />
        : <StartAudio className="btn btn-secondary btn-sm invite" label="Включить звук" />}
    </header>
  );
}

function CallDuration({ startedAt }: { startedAt: number }) {
  const [seconds, setSeconds] = useState(() => Math.floor((performance.now() - startedAt) / 1000));
  useEffect(() => {
    const interval = window.setInterval(() => setSeconds(Math.floor((performance.now() - startedAt) / 1000)), 250);
    return () => window.clearInterval(interval);
  }, [startedAt]);

  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor(seconds / 60) % 60;
  const duration = [minutes, seconds % 60].map((part) => String(part).padStart(2, "0")).join(":");

  return (
    <span className="call-duration" role="timer" aria-label="Длительность встречи" title="Длительность встречи">
      {hours > 0 ? `${hours}:${duration}` : duration}
    </span>
  );
}

/**
 * Copies the link or opens the share sheet on phones.
 * While nobody else has joined it is filled with the accent color, then it turns quiet.
 */
function InviteButton({ meetingId, prominent }: { meetingId: string; prominent: boolean }) {
  const url = meetingUrl(meetingId);
  const { copied, copy } = useCopyFeedback(url, "Не получилось скопировать ссылку. Скопируйте её из адресной строки.");
  const canShare = typeof navigator.share === "function" && window.matchMedia("(pointer: coarse)").matches;

  const onClick = async () => {
    if (canShare && (await shareLink(url))) return;
    await copy();
  };

  const action = canShare ? "Отправить ссылку на встречу" : "Скопировать ссылку на встречу";
  return (
    <button
      className={`btn btn-sm invite ${prominent ? "btn-primary" : "btn-secondary"}`}
      onClick={onClick}
      aria-label={`Пригласить: ${action.toLowerCase()}`}
      title={action}
      data-copied={copied}
    >
      <LinkIcon />
      <span className="invite-label">Пригласить</span>
      <span className="invite-state" aria-hidden="true">
        {copied ? <CheckIcon className="copy-check" /> : canShare ? <ShareIcon /> : <CopyIcon />}
      </span>
    </button>
  );
}

/** Grid of cameras; when someone shares a screen, the screen goes big and cameras move to a strip. */
function Stage() {
  const tracks = useTracks(
    [
      { source: Track.Source.Camera, withPlaceholder: true },
      { source: Track.Source.ScreenShare, withPlaceholder: false },
    ],
    { updateOnlyOn: [RoomEvent.ActiveSpeakersChanged], onlySubscribed: false },
  );
  const layout = useLayoutContext();
  const focus = usePinnedTracks(layout)?.[0];
  const screens = tracks.filter(isTrackReference).filter((t) => t.publication.source === Track.Source.ScreenShare);
  const autoPinned = useRef<TrackReferenceOrPlaceholder | null>(null);

  // Pin a new screen share automatically, unpin when it stops
  useEffect(() => {
    const first = screens.find((s) => s.publication.isSubscribed || s.participant.isLocal);
    if (first && !autoPinned.current) {
      layout.pin.dispatch?.({ msg: "set_pin", trackReference: first });
      autoPinned.current = first;
    } else if (autoPinned.current && !screens.some((s) => isEqualTrackRef(s, autoPinned.current!))) {
      layout.pin.dispatch?.({ msg: "clear_pin" });
      autoPinned.current = null;
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [screens.map((s) => `${s.publication.trackSid}_${s.publication.isSubscribed}`).join()]);

  const others = tracks.filter((t) => !focus || !isEqualTrackRef(t, focus));

  return (
    <div className="stage">
      {focus ? (
        <FocusLayoutContainer className="stage-focus">
          <CarouselLayout tracks={others}>
            <MeetingTile />
          </CarouselLayout>
          <MeetingTile trackRef={focus} />
        </FocusLayoutContainer>
      ) : (
        <GridLayout tracks={tracks} className="stage-grid">
          <MeetingTile />
        </GridLayout>
      )}
    </div>
  );
}

function MeetingTile({ trackRef }: { trackRef?: TrackReferenceOrPlaceholder }) {
  const track = useEnsureTrackRef(trackRef);
  const { name } = useParticipantInfo({ participant: track.participant });
  const pinned = usePinnedTracks(useLayoutContext())?.[0];
  const focused = !!pinned && isEqualTrackRef(track, pinned);
  const title = `${focused ? "Вернуться к сетке" : "Показать крупнее"}: ${name || "Гость"}`;
  const initials = (name?.trim() || "Гость").split(/\s+/).slice(0, 2).map((part) => Array.from(part)[0]).join("").toUpperCase();
  const labelControl = useCallback((tile: HTMLDivElement | null) => {
    // LiveKit renders this control inside ParticipantTile and exposes no label prop.
    const toggle = tile?.querySelector(".lk-focus-toggle-button");
    toggle?.setAttribute("aria-label", title);
    toggle?.setAttribute("title", title);
  }, [title]);
  return <ParticipantTile trackRef={track} data-initials={initials} ref={labelControl} />;
}

function isEqualTrackRef(a: TrackReferenceOrPlaceholder, b: TrackReferenceOrPlaceholder): boolean {
  return (
    a.participant.identity === b.participant.identity &&
    a.source === b.source &&
    a.publication?.trackSid === b.publication?.trackSid
  );
}

function Controls({ direct }: { direct: boolean }) {
  const room = useRoomContext();
  const toast = useToast();

  const mic = useTrackToggle({
    source: Track.Source.Microphone,
    onDeviceError: () => toast("Не получилось включить микрофон. Проверьте доступ к нему.", "error"),
  });
  const cam = useTrackToggle({
    source: Track.Source.Camera,
    onDeviceError: () => toast("Не получилось включить камеру. Проверьте доступ к ней.", "error"),
  });
  const screen = useTrackToggle({
    source: Track.Source.ScreenShare,
    captureOptions: {
      audio: true, // Chrome can share tab/system sound along with the picture
      contentHint: "detail",
      selfBrowserSurface: "exclude",
      surfaceSwitching: "include",
      resolution: ScreenSharePresets.h1080fps15.resolution,
    },
    onDeviceError: (e) => {
      // Closing the picker without choosing is not an error worth a message
      if ((e as { name?: string }).name !== "NotAllowedError") {
        toast("Не получилось начать показ экрана. Попробуйте ещё раз или выберите другое окно.", "error");
      }
    },
  });

  return (
    <div className="controls" role="group" aria-label={direct ? "Управление звонком" : "Управление встречей"}>
      <div className="controls-group">
        <ControlWithDevices kind="audioinput" label="Микрофон">
          <ControlButton
            on={mic.enabled}
            pending={mic.pending}
            onClick={() => mic.toggle()}
            iconOn={<MicIcon />}
            iconOff={<MicOffIcon />}
            titleOn="Выключить микрофон"
            titleOff="Включить микрофон"
            caption="Микрофон"
          />
        </ControlWithDevices>
        <ControlWithDevices kind="videoinput" label="Камера">
          <ControlButton
            on={cam.enabled}
            pending={cam.pending}
            onClick={() => cam.toggle()}
            iconOn={<CamIcon />}
            iconOff={<CamOffIcon />}
            titleOn="Выключить камеру"
            titleOff="Включить камеру"
            caption="Камера"
          />
        </ControlWithDevices>
        {canShareScreen && (
          <ControlButton
            on={screen.enabled}
            pending={screen.pending}
            onClick={() => screen.toggle()}
            iconOn={<ScreenStopIcon />}
            iconOff={<ScreenIcon />}
            titleOn="Остановить показ экрана"
            titleOff="Показать экран"
            caption="Экран"
            offStyle="neutral"
          />
        )}
      </div>
      <button className="ctl ctl-leave" onClick={() => room.disconnect()} title={direct ? "Завершить звонок" : "Выйти из встречи"}>
        <span className="ctl-icon">
          <HangUpIcon />
        </span>
        <span className="ctl-caption">{direct ? "Завершить" : "Выйти"}</span>
      </button>
    </div>
  );
}

/**
 * A toggle: the visible caption is its name, aria-pressed is its state,
 * the tooltip says what a click will do.
 * Microphone and camera highlight the muted state. Screen sharing highlights the active state.
 */
function ControlButton(props: {
  on: boolean;
  pending: boolean;
  onClick: () => void;
  iconOn: React.ReactNode;
  iconOff: React.ReactNode;
  titleOn: string;
  titleOff: string;
  caption: string;
  offStyle?: "neutral";
}) {
  const media = props.offStyle !== "neutral";
  const state = props.on ? (media ? "" : "ctl-active") : media ? "ctl-off" : "";
  return (
    <button
      className={`ctl ${state}`}
      onClick={props.onClick}
      disabled={props.pending}
      aria-pressed={props.on}
      title={props.on ? props.titleOn : props.titleOff}
    >
      <span className="ctl-icon"><span key={String(props.on)} className="ctl-glyph">{props.on ? props.iconOn : props.iconOff}</span></span>
      <span className="ctl-caption">{props.caption}</span>
    </button>
  );
}

/** A control with a small arrow that opens the device list (shown only with 2+ devices). */
function ControlWithDevices({
  kind,
  label,
  children,
}: {
  kind: MediaDeviceKind;
  label: string;
  children: React.ReactNode;
}) {
  const room = useRoomContext();
  const { devices, activeDeviceId, setActiveMediaDevice } = useMediaDeviceSelect({ kind, room });
  const [open, setOpen] = useState(false);
  const menuId = useId();
  const wrap = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const menu = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    // Start on the device in use, so Enter keeps the current choice
    (menu.current?.querySelector<HTMLButtonElement>("[aria-pressed=true]") ??
      menu.current?.querySelector<HTMLButtonElement>("button"))?.focus();
    const close = (e: Event) => {
      if (e instanceof KeyboardEvent) {
        if (e.key === "Escape") {
          setOpen(false);
          trigger.current?.focus();
        }
      } else if (!wrap.current?.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener("pointerdown", close);
    document.addEventListener("focusin", close);
    document.addEventListener("keydown", close);
    return () => {
      document.removeEventListener("pointerdown", close);
      document.removeEventListener("focusin", close);
      document.removeEventListener("keydown", close);
    };
  }, [open]);

  const list = devices.filter((d) => d.deviceId && d.deviceId !== "default");
  const chooseLabel = kind === "audioinput" ? "Выбрать микрофон" : "Выбрать камеру";
  if (list.length < 2) return <>{children}</>;

  return (
    <div className="ctl-split" ref={wrap}>
      {children}
      <button
        ref={trigger}
        className="ctl-more"
        onClick={() => setOpen(!open)}
        aria-expanded={open}
        aria-controls={open ? menuId : undefined}
        aria-label={chooseLabel}
        title={chooseLabel}
      >
        <ChevronUpIcon />
      </button>
      {open && (
        <div id={menuId} className="device-menu" ref={menu} role="group" aria-label={chooseLabel}>
          {list.map((d, i) => (
            <button
              key={d.deviceId}
              aria-pressed={d.deviceId === activeDeviceId}
              className="device-menu-item"
              onClick={() => {
                setActiveMediaDevice(d.deviceId);
                setOpen(false);
                trigger.current?.focus();
              }}
            >
              {d.deviceId === activeDeviceId && <CheckIcon />}
              {d.label || `${label} ${i + 1}`}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
