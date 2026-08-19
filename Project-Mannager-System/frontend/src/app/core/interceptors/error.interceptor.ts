import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ErrorResponse } from '../models/common.model';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      let formattedError: ErrorResponse = {
        timestamp: new Date().toISOString(),
        status: error.status || 500,
        error: error.statusText || 'Error',
        code: 'UNKNOWN_ERROR',
        message: 'Có lỗi xảy ra khi kết nối máy chủ',
        path: req.url
      };

      if (error.error && typeof error.error === 'object') {
        formattedError = {
          ...formattedError,
          ...error.error
        };
      }

      console.error(`[API Error ${formattedError.status}] ${formattedError.code}: ${formattedError.message}`, formattedError);
      return throwError(() => formattedError);
    })
  );
};
