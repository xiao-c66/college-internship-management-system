import { defineStore } from 'pinia';
import service from '@/utils/request';

export interface UserState {
  token: string;
  userId: number | null;
  username: string;
  realName: string;
  userType: string;
  roleCode: string;
  deptId: number | null;
  deptName: string;
  permissions: string[];
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    token: localStorage.getItem('token') || '',
    userId: null,
    username: localStorage.getItem('username') || '',
    realName: localStorage.getItem('realName') || '',
    userType: localStorage.getItem('userType') || '',
    roleCode: localStorage.getItem('roleCode') || '',
    deptId: null,
    deptName: localStorage.getItem('deptName') || '',
    permissions: JSON.parse(localStorage.getItem('permissions') || '[]')
  }),

  getters: {
    isLoggedIn: (state) => !!state.token,
    role: (state) => state.userType
  },

  actions: {
    setLoginInfo(data: any) {
      this.token = data.token || '';
      this.userId = data.userId || null;
      this.username = data.username || '';
      this.realName = data.realName || '';
      this.userType = data.userType || '';
      this.roleCode = data.roleCode || '';
      this.deptId = data.deptId || null;
      this.deptName = data.deptName || '';
      this.permissions = data.permissions || [];

      localStorage.setItem('token', this.token);
      localStorage.setItem('username', this.username);
      localStorage.setItem('realName', this.realName);
      localStorage.setItem('userType', this.userType);
      localStorage.setItem('roleCode', this.roleCode);
      localStorage.setItem('deptName', this.deptName);
      localStorage.setItem('permissions', JSON.stringify(this.permissions));
    },

    async getUserInfo() {
      if (!this.token) return null;
      try {
        const res: any = await service.get('/auth/me');
        if (res && res.code === 200 && res.data) {
          const d = res.data;
          this.userId = d.userId;
          this.username = d.username;
          this.realName = d.realName;
          this.userType = d.userType;
          this.deptId = d.deptId;
          this.deptName = d.deptName;
          this.permissions = d.permissions || [];

          localStorage.setItem('username', d.username);
          localStorage.setItem('realName', d.realName);
          localStorage.setItem('userType', d.userType);
          localStorage.setItem('deptName', d.deptName || '');
          localStorage.setItem('permissions', JSON.stringify(d.permissions || []));
          return d;
        }
      } catch (err) {
        this.clearSession();
        throw err;
      }
    },

    async logout() {
      try {
        if (this.token) {
          await service.post('/auth/logout');
        }
      } catch (e) {
        console.warn('Logout request completed, clearing local state', e);
      } finally {
        this.clearSession();
      }
    },

    clearSession() {
      this.token = '';
      this.userId = null;
      this.username = '';
      this.realName = '';
      this.userType = '';
      this.roleCode = '';
      this.deptId = null;
      this.deptName = '';
      this.permissions = [];

      localStorage.removeItem('token');
      localStorage.removeItem('username');
      localStorage.removeItem('realName');
      localStorage.removeItem('userType');
      localStorage.removeItem('roleCode');
      localStorage.removeItem('deptName');
      localStorage.removeItem('permissions');
    }
  }
});
