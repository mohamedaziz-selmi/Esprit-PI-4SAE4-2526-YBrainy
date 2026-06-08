import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { GenerateApplicationRequest, GenerateApplicationResponse } from '../models/application-generator.models';

@Injectable({ providedIn: 'root' })
export class ApplicationGeneratorStoreService {
  private readonly apiBaseUrl = '/api';

  constructor(private http: HttpClient) {}

  generate(payload: GenerateApplicationRequest): Observable<GenerateApplicationResponse> {
    return this.http.post<GenerateApplicationResponse>(`${this.apiBaseUrl}/generate-application`, payload);
  }
}
