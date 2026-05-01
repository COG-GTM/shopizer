"use client";

import { createContext, useContext, useState, useEffect, ReactNode } from "react";

interface User {
  token: string;
  role: "customer" | "admin";
}

interface AuthContextType {
  user: User | null;
  login: (token: string, role: "customer" | "admin") => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType>({
  user: null,
  login: () => {},
  logout: () => {},
});

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);

  useEffect(() => {
    const stored = localStorage.getItem("shopizer_auth");
    if (stored) {
      try {
        setUser(JSON.parse(stored));
      } catch {
        localStorage.removeItem("shopizer_auth");
      }
    }
  }, []);

  const login = (token: string, role: "customer" | "admin") => {
    const u = { token, role };
    setUser(u);
    localStorage.setItem("shopizer_auth", JSON.stringify(u));
  };

  const logout = () => {
    setUser(null);
    localStorage.removeItem("shopizer_auth");
  };

  return (
    <AuthContext.Provider value={{ user, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
