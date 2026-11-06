import { useEffect, useRef, useState } from "react";
import {
  CarouselLayout,
  ConnectionStateToast,
  FocusLayout,
  FocusLayoutContainer,
  GridLayout,
  LayoutContextProvider,
  LiveKitRoom,
  ParticipantTile,
  RoomAudioRenderer,
  useCreateLayoutContext,
  useMediaDeviceSelect,
  usePinnedTracks,
  useParticipants,
  useRoomContext,
  isTrackReference,
  useLayoutContext,
  useTrackToggle,
  useTracks,
  type TrackReferenceOrPlaceholder,
} from "@livekit/components-react";
import "@livekit/components-styles";
import { DisconnectReason, RoomEvent, ScreenSharePresets, Track, type RoomOptions } from "livekit-client";
import type { JoinResponse } from "../api";
import {
  CamIcon,
  CamOffIcon,
  ChevronUpIcon,
  HangUpIcon,
  LinkIcon,
  MicIcon,
  MicOffIcon,
  ScreenIcon,
  ScreenStopIcon,
  UsersIcon,
} from "../icons";
import { canShareScreen, copyText, meetingUrl, plural } from "../lib";
import { LinkBox } from "./LinkBox";
import type { JoinChoices } from "./PreJoin";
import { useToast } from "./toast-context";

export type LeaveReason = "left" | "ended" | "lost";

const ROOM_OPTIONS: RoomOptions = {
  adaptiveStream: true,
  dynacast: true,
  publishDefaults: {
    // Sharp text on shared screens matters more than smooth motion
    screenShareEncoding: ScreenSharePresets.h1080fps15.encoding,
  },
};

type Props = {
  conn: JoinResponse;
  choices: JoinChoices;
  onLeave: (reason: LeaveReason) => void;
};

export function CallRoom({ conn, choices, onLeave }: Props) {
  const toast = useToast();
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
      onError={() => toast("Не получилось подключиться к встрече. Проверьте интернет и войдите снова.", "error")}
      data-lk-theme="default"
      className="call"
    >
      <CallLayout meetingId={conn.meeting_id} />
      <RoomAudioRenderer />
      <ConnectionStateToast />
    </LiveKitRoom>
  );
}

function CallLayout({ meetingId }: { meetingId: string }) {
  const layout = useCreateLayoutContext();
  return (
    <LayoutContextProvider value={layout}>
      <div className="call-shell">
        <CallHeader meetingId={meetingId} />
        <Stage meetingId={meetingId} />
        <Controls meetingId={meetingId} />
      </div>
    </LayoutContextProvider>
  );
}

function CallHeader({ meetingId }: { meetingId: string }) {
  const count = useParticipants().length;
  return (
    <header className="call-header">
      <span className="call-code">{meetingId}</span>
      <span className="call-count" title="Участники">
        <UsersIcon />
        <span>
          {count}
          <span className="visually-hidden"> {plural(count, ["участник", "участника", "участников"])}</span>
        </span>
      </span>
    </header>
  );
}

/** Grid of cameras; when someone shares a screen, the screen goes big and cameras move to a strip. */
function Stage({ meetingId }: { meetingId: string }) {
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
  const alone = new Set(tracks.map((t) => t.participant.identity)).size <= 1;

  return (
    <div className="stage">
      {focus ? (
        <FocusLayoutContainer className="stage-focus">
          <CarouselLayout tracks={others}>
            <ParticipantTile />
          </CarouselLayout>
          <FocusLayout trackRef={focus} />
        </FocusLayoutContainer>
      ) : (
        <GridLayout tracks={tracks} className="stage-grid">
          <ParticipantTile />
        </GridLayout>
      )}
      {alone && !focus && (
        <div className="alone-card">
          <p className="alone-title">Пока здесь только вы</p>
          <p className="muted">Отправьте ссылку, чтобы пригласить собеседника.</p>
          <LinkBox meetingId={meetingId} />
        </div>
      )}
    </div>
  );
}

function isEqualTrackRef(a: TrackReferenceOrPlaceholder, b: TrackReferenceOrPlaceholder): boolean {
  return (
    a.participant.identity === b.participant.identity &&
    a.source === b.source &&
    a.publication?.trackSid === b.publication?.trackSid
  );
}

function Controls({ meetingId }: { meetingId: string }) {
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

  const copyLink = async () => {
    const ok = await copyText(meetingUrl(meetingId));
    toast(
      ok ? "Ссылка скопирована" : "Не получилось скопировать ссылку. Скопируйте её из адресной строки.",
      ok ? "info" : "error",
    );
  };

  return (
    <nav className="controls" aria-label="Управление встречей">
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
        <button className="ctl" onClick={copyLink} title="Скопировать ссылку на встречу">
          <span className="ctl-icon">
            <LinkIcon />
          </span>
          <span className="ctl-caption">Ссылка</span>
        </button>
      </div>
      <button className="ctl ctl-leave" onClick={() => room.disconnect()} title="Выйти из встречи">
        <span className="ctl-icon">
          <HangUpIcon />
        </span>
        <span className="ctl-caption">Выйти</span>
      </button>
    </nav>
  );
}

/**
 * A toggle: the visible caption is its name, aria-pressed is its state,
 * the tooltip says what a click will do.
 * offStyle "danger" (mic, camera) paints the off state red; "neutral" (screen) paints the on state blue.
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
  offStyle?: "danger" | "neutral";
}) {
  const danger = (props.offStyle ?? "danger") === "danger";
  const state = props.on ? (danger ? "" : "ctl-active") : danger ? "ctl-off" : "";
  return (
    <button
      className={`ctl ${state}`}
      onClick={props.onClick}
      disabled={props.pending}
      aria-pressed={props.on}
      title={props.on ? props.titleOn : props.titleOff}
    >
      <span className="ctl-icon">{props.on ? props.iconOn : props.iconOff}</span>
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
    document.addEventListener("keydown", close);
    return () => {
      document.removeEventListener("pointerdown", close);
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
        aria-label={chooseLabel}
        title={chooseLabel}
      >
        <ChevronUpIcon />
      </button>
      {open && (
        <div className="device-menu" ref={menu} role="group" aria-label={chooseLabel}>
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
              {d.label || `${label} ${i + 1}`}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
