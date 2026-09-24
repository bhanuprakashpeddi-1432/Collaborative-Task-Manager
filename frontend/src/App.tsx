import { Routes, Route, Navigate } from 'react-router-dom';
import { Login } from './pages/Login';
import { Workspace } from './pages/Workspace';
import { useAuthStore } from './store/useAuthStore';

import { Register } from './pages/Register';

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated());
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" replace />;
}

function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route 
        path="/workspace" 
        element={
          <ProtectedRoute>
            <Workspace />
          </ProtectedRoute>
        } 
      />
      <Route path="*" element={<Navigate to="/workspace" replace />} />
    </Routes>
  );
}

export default App;
