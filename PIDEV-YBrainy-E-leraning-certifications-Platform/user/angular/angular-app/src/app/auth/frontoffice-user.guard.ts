import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { getRealmRoles, isAuthenticated, redirectToAppLogin } from './keycloak.service';

export const frontofficeUserGuard: CanActivateFn = () => {
  if (!isAuthenticated()) {
    redirectToAppLogin(window.location.href);
    return false;
  }

  const isAdmin = getRealmRoles().some((role) => String(role).toUpperCase() === 'ADMIN');
  if (isAdmin) {
    return inject(Router).createUrlTree(['/dashboard']);
  }

  return true;
};
