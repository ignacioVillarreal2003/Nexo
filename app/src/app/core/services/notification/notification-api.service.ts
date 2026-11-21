import {Injectable} from '@angular/core';
import {environment} from '@env/environment';
import {BaseApiService} from '@core/services/http/base-api.service';

/** API de notificaciones: bandeja, preferencias y administración de categorías (notification). */
@Injectable({providedIn: 'root'})
export class NotificationApiService extends BaseApiService {
  protected readonly base = environment.notificationApiUrl;
}
