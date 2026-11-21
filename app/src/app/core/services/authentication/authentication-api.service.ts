import {Injectable} from '@angular/core';
import {environment} from '@env/environment';
import {BaseApiService} from '@core/services/http/base-api.service';

/** API de autenticación: cuenta, sesiones, usuarios, roles y permisos (authentication). */
@Injectable({providedIn: 'root'})
export class AuthenticationApiService extends BaseApiService {
  protected readonly base = environment.authenticationApiUrl;
}
