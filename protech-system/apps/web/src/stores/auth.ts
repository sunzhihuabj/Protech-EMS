import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export interface Me {
  user: { id: string; username: string; displayName: string; deptId: string | null; dept?: { name: string } | null };
  roles: Array<{ code: string; name: string; maxDataScope: string }>;
  permissions: string[];
  pmGrants: Array<{ projectId: string; expired: boolean }>;
}

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  me: Me | null;
  setTokens: (access: string, refresh: string) => void;
  setMe: (me: Me) => void;
  clear: () => void;
  has: (perm: string) => boolean;
}

/** 会话态（persist 到 localStorage；8h 会话由 refresh 轮换维持，FR-M00-004） */
export const useAuth = create<AuthState>()(
  persist(
    (set, get) => ({
      accessToken: null,
      refreshToken: null,
      me: null,
      setTokens: (accessToken, refreshToken) => set({ accessToken, refreshToken }),
      setMe: (me) => set({ me }),
      clear: () => set({ accessToken: null, refreshToken: null, me: null }),
      has: (perm) => {
        const ps = get().me?.permissions ?? [];
        return ps.includes('*') || ps.includes(perm);
      },
    }),
    { name: 'protech-auth' },
  ),
);
