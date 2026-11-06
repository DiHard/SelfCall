import { useState } from "react";
import { CheckIcon, CopyIcon, ShareIcon } from "../icons";
import { copyText, meetingUrl, shareLink } from "../lib";
import { useToast } from "./toast-context";

/** The meeting link with a copy button (and the share sheet on phones). */
export function LinkBox({ meetingId }: { meetingId: string }) {
  const url = meetingUrl(meetingId);
  const toast = useToast();
  const [copied, setCopied] = useState(false);
  const canShare = typeof navigator.share === "function" && window.matchMedia("(pointer: coarse)").matches;

  const copy = async () => {
    if (await copyText(url)) {
      setCopied(true);
      toast("Ссылка скопирована");
      window.setTimeout(() => setCopied(false), 2000);
    } else {
      toast("Не получилось скопировать. Выделите ссылку и скопируйте вручную.", "error");
    }
  };

  return (
    <div className="linkbox">
      <input
        className="linkbox-url"
        readOnly
        value={url.replace(/^https?:\/\//, "")}
        aria-label="Ссылка на встречу"
        onFocus={(e) => e.currentTarget.select()}
      />
      {canShare ? (
        <button className="btn btn-secondary btn-sm" onClick={() => shareLink(url)}>
          <ShareIcon />
          Отправить
        </button>
      ) : (
        <button className="btn btn-secondary btn-sm" onClick={copy}>
          {copied ? <CheckIcon /> : <CopyIcon />}
          {copied ? "Скопировано" : "Скопировать"}
        </button>
      )}
    </div>
  );
}
