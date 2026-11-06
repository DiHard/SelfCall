import { createContext, useContext } from "react";

export type ToastTone = "info" | "error";
export type ShowToast = (text: string, tone?: ToastTone) => void;

export const ToastContext = createContext<ShowToast>(() => {});

export function useToast(): ShowToast {
  return useContext(ToastContext);
}
