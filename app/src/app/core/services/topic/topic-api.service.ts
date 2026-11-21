import {Injectable} from '@angular/core';
import {environment} from '@env/environment';
import {BaseApiService} from '@core/services/http/base-api.service';

/** API del catálogo de tópicos (topic). */
@Injectable({providedIn: 'root'})
export class TopicApiService extends BaseApiService {
  protected readonly base = environment.topicApiUrl;
}
