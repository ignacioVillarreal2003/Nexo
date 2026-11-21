import {Injectable} from '@angular/core';
import {environment} from '@env/environment';
import {BaseApiService} from '@core/services/http/base-api.service';

/** API de suscripciones y configuración de entrega (subscription). */
@Injectable({providedIn: 'root'})
export class SubscriptionApiService extends BaseApiService {
  protected readonly base = environment.subscriptionApiUrl;
}
