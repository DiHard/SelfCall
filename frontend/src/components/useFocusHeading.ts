import { useEffect, useRef } from "react";

/** Moves focus to a view's heading when it appears, so screen readers announce the new view. */
export function useFocusHeading<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  useEffect(() => {
    ref.current?.focus({ preventScroll: true });
  }, []);
  return ref;
}
