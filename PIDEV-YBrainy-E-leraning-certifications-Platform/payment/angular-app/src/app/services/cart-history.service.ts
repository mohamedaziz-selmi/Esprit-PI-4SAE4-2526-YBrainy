import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { CartHistoryRecord } from '../models/cart-history.model';

@Injectable({
  providedIn: 'root'
})
export class CartHistoryService {
  private readonly apiUrl = `${environment.apiUrl}/cart/history`;

  constructor(private http: HttpClient) { }

  getHistory(): Observable<CartHistoryRecord[]> {
    return this.http.get<CartHistoryRecord[]>(this.apiUrl);
  }
}
