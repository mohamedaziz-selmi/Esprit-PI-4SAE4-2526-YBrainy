import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { PackConversionSummary } from '../models/pack-conversion.model';

@Injectable({
  providedIn: 'root'
})
export class PackConversionService {
  private readonly apiUrl = `${environment.apiUrl}/admin/packs/conversion`;

  constructor(private http: HttpClient) { }

  getSummary(limit = 10): Observable<PackConversionSummary> {
    const params = new HttpParams().set('limit', String(limit));
    return this.http.get<PackConversionSummary>(`${this.apiUrl}/summary`, { params });
  }
}
