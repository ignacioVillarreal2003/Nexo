import {Injectable} from '@angular/core';
import {environment} from '@env/environment';
import {BaseApiService} from '@core/services/http/base-api.service';

/** API de entregas: Mi día, historial y favoritos (delivery). */
@Injectable({providedIn: 'root'})
export class DeliveryApiService extends BaseApiService {
  protected readonly base = environment.deliveryApiUrl;
}
