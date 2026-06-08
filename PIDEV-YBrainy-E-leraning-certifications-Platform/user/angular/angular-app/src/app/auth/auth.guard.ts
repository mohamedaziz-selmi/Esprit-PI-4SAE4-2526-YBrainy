import { CanActivateFn } from '@angular/router';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { isAuthenticated, redirectToAppLogin } from './keycloak.service';
import { UserSessionService } from '../tracking/user-session.service';

export const authGuard: CanActivateFn = (_route, state) => {
  const router = inject(Router);
  const userSession = inject(UserSessionService);
  const requestedUrl = state.url || router.url || window.location.href;
  const requestedPath = requestedUrl.split('?')[0].split('#')[0] || '/';
  const isRootNavigation = requestedPath === '/' || requestedPath === '';

  console.log('[AUTH GUARD] Running...');
  console.log('[AUTH GUARD] isAuthenticated:', isAuthenticated());

  if (!isAuthenticated()) {
    console.log('[AUTH GUARD] Not authenticated, redirecting to login');
    redirectToAppLogin(requestedUrl);
    return false;
  }

  // Set default mode on first login (if not already set)
  const userRole = userSession.get()?.role;
  const storedMode = localStorage.getItem('ybrainy_user_mode');

  console.log('[AUTH GUARD] User role:', userRole);
  console.log('[AUTH GUARD] Stored mode:', storedMode);
  console.log('[AUTH GUARD] User session:', userSession.get());

  if (!storedMode && userRole) {
    console.log('[AUTH GUARD] First login detected, setting mode to:', userRole);
    // First time - set mode to role
    userSession.setMode(userRole as 'STUDENT' | 'INSTRUCTOR' | 'ADMIN');
  }

  console.log('[AUTH GUARD] Returning true (allowing access)');
  return true;
};
