import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);

  const authReq = req.clone({ withCredentials: true });

  return next(authReq).pipe(
    catchError(error => {
      if (error.status === 401 && !req.url.includes('/api/auth/')) {
        router.navigate(['/login']);
      }
      // Le backend ferme l'API tant que le mot de passe expire n'est pas change.
      if (error.status === 403 && error.error?.passwordExpired) {
        router.navigate(['/change-password']);
      }
      return throwError(() => error);
    })
  );
};
