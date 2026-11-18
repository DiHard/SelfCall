import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { ToastProvider } from "./components/Toast";
import { useReleaseUpdate } from "./components/useReleaseUpdate";
import { CallsPage } from "./pages/CallsPage";
import { HomePage } from "./pages/HomePage";
import { MeetingPage } from "./pages/MeetingPage";

export default function App() {
  const dismissUpdate = useReleaseUpdate();

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<ToastProvider dismissUpdate={dismissUpdate}><HomePage /></ToastProvider>} />
        <Route path="/calls" element={<ToastProvider><CallsPage /></ToastProvider>} />
        <Route path="/m/:meetingId" element={<MeetingPage dismissUpdate={dismissUpdate} />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
