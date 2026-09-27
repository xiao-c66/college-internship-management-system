import { create } from 'zustand';
import type { SysUser, UserType } from '../types/auth';

interface AuthState {
  token: string | null;
  user: SysUser | null;
  userType: UserType | null;
  isAuthenticated: boolean;
  setAuth: (token: string, user: SysUser) => void;
  logout: () => void;
  hasRole: (roles: UserType | UserType[]) => boolean;
}

const getInitialUser = (): SysUser | null => {
  try {
    const raw = localStorage.getItem('user');
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
};

const initialToken = localStorage.getItem('token');
const initialUser = getInitialUser();

export const useAuthStore = create<AuthState>((set, get) => ({
  token: initialToken,
  user: initialUser,
  userType: initialUser ? initialUser.userType : null,
  isAuthenticated: !!initialToken,

  setAuth: (token: string, user: SysUser) => {
    localStorage.setItem('token', token);
    localStorage.setItem('user', JSON.stringify(user));
    localStorage.setItem('userType', user.userType);
    set({
      token,
      user,
      userType: user.userType,
      isAuthenticated: true
    });
  },

  logout: () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    localStorage.removeItem('userType');
    set({
      token: null,
      user: null,
      userType: null,
      isAuthenticated: false
    });
  },

  hasRole: (roles: UserType | UserType[]) => {
    const current = get().userType;
    if (!current) return false;
    if (current === 'SYS_ADMIN') return true; // 系统管理员拥有全局权限
    if (Array.isArray(roles)) {
      return roles.includes(current);
    }
    return current === roles;
  }
}));
