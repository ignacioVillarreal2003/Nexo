import {Injectable} from '@angular/core';
import {environment} from '@env/environment';
import {BaseApiService} from '@core/services/http/base-api.service';

/** API de contenidos y del pipeline de recopilación (content). */
@Injectable({providedIn: 'root'})
export class ContentApiService extends BaseApiService {
  protected readonly base = environment.contentApiUrl;
}
