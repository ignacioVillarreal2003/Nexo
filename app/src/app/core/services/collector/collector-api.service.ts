import {Injectable} from '@angular/core';
import {environment} from '@env/environment';
import {BaseApiService} from '@core/services/http/base-api.service';

/** API del collector: recopilaciones manuales y estado de la ejecución en curso. */
@Injectable({providedIn: 'root'})
export class CollectorApiService extends BaseApiService {
  protected readonly base = environment.collectorApiUrl;
}
