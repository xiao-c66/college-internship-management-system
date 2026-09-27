import React from 'react';
import { useAuthStore } from '../store/useAuthStore';
import type { UserType } from '../types/auth';
import { ForbiddenPage } from '../pages/error/ForbiddenPage';

interface RoleGuardProps {
  allowedRoles: UserType[];
  children?: React.ReactNode;
}

export const RoleGuard: React.FC<RoleGuardProps> = ({ allowedRoles, children }) => {
  const { hasRole } = useAuthStore();

  if (!hasRole(allowedRoles)) {
    return <ForbiddenPage />;
  }

  return children ? <>{children}</> : null;
};
