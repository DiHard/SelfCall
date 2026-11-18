import { useEffect, useState } from "react";
import { copyText } from "../lib";
import { useToast } from "./toast-context";

export function useCopyFeedback(url: string, errorMessage: string) {
  const toast = useToast();
  const [copiedAt, setCopiedAt] = useState(0);

  useEffect(() => {
    if (!copiedAt) return;
    const timeout = window.setTimeout(() => setCopiedAt(0), 2300);
    return () => window.clearTimeout(timeout);
  }, [copiedAt]);

  async function copy() {
    if (await copyText(url)) {
      setCopiedAt(performance.now());
      toast("Ссылка скопирована");
    } else {
      toast(errorMessage, "error");
    }
  }

  return { copied: copiedAt > 0, copy };
}
