import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { ToastProvider } from "./components/Toast";
import { HomePage } from "./pages/HomePage";
import { MeetingPage } from "./pages/MeetingPage";

export default function App() {
  return (
    <ToastProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/m/:meetingId" element={<MeetingPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </ToastProvider>
  );
}
