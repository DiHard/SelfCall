import { CheckIcon, CopyIcon, LinkIcon, ShareIcon } from "../icons";
import { meetingUrl, shareLink } from "../lib";
import { useCopyFeedback } from "./useCopyFeedback";

/** The meeting link with a copy button (and the share sheet on phones). */
export function LinkBox({ meetingId, justCreated }: { meetingId: string; justCreated: boolean }) {
  const url = meetingUrl(meetingId);
  const { copied, copy } = useCopyFeedback(url, "Не получилось скопировать. Выделите ссылку и скопируйте вручную.");
  const canShare = typeof navigator.share === "function" && window.matchMedia("(pointer: coarse)").matches;

  return (
    <div className={`linkbox${justCreated ? " linkbox-ready" : ""}`} data-copied={copied}>
      <LinkIcon className="linkbox-mark" />
      <div className="linkbox-info">
        <label htmlFor="meeting-link">{copied ? "Ссылка скопирована" : "Ссылка на встречу"}</label>
        <input
          id="meeting-link"
          className="linkbox-url"
          readOnly
          value={url}
          onFocus={(e) => e.currentTarget.select()}
        />
      </div>
      <button
        className="btn btn-icon linkbox-action"
        type="button"
        onClick={async () => { if (!canShare || !(await shareLink(url))) await copy(); }}
        aria-label={canShare ? "Отправить ссылку" : "Скопировать ссылку"}
        title={canShare ? "Отправить ссылку" : "Скопировать ссылку"}
      >
        {copied ? <CheckIcon className="copy-check" /> : canShare ? <ShareIcon /> : <CopyIcon />}
      </button>
    </div>
  );
}
