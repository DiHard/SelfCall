import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { NameScreen } from "./pages/NameScreen";
import { RoomsScreen } from "./pages/RoomsScreen";
import { CallScreen } from "./pages/CallScreen";

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<NameScreen />} />
        <Route path="/rooms" element={<RoomsScreen />} />
        <Route path="/room/:roomName" element={<CallScreen />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
