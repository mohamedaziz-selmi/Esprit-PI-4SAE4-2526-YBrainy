import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Behavior, BehaviorRequest, Personality, PersonalityRequest } from '../models/personality.model';

@Injectable({
  providedIn: 'root'
})
export class PersonalityService {
  private readonly personalityApiUrl = '/api/personalities';
  private readonly behaviorApiUrl = '/api/behaviors';

  constructor(private http: HttpClient) {}

  getAllPersonalities(): Observable<Personality[]> {
    return this.http.get<Personality[]>(this.personalityApiUrl);
  }

  getPersonalityById(id: string): Observable<Personality> {
    return this.http.get<Personality>(`${this.personalityApiUrl}/${id}`);
  }

  getPersonalityByUserId(userId: number): Observable<Personality> {
    return this.http.get<Personality>(`${this.personalityApiUrl}/user/${userId}`);
  }

  createPersonality(request: PersonalityRequest): Observable<Personality> {
    return this.http.post<Personality>(this.personalityApiUrl, request);
  }

  updatePersonality(id: string, request: PersonalityRequest): Observable<Personality> {
    return this.http.put<Personality>(`${this.personalityApiUrl}/${id}`, request);
  }

  deletePersonality(id: string): Observable<void> {
    return this.http.delete<void>(`${this.personalityApiUrl}/${id}`);
  }

  getAllBehaviors(): Observable<Behavior[]> {
    return this.http.get<Behavior[]>(this.behaviorApiUrl);
  }

  getBehaviorById(id: string): Observable<Behavior> {
    return this.http.get<Behavior>(`${this.behaviorApiUrl}/${id}`);
  }

  getBehaviorByUserId(userId: number): Observable<Behavior> {
    return this.http.get<Behavior>(`${this.behaviorApiUrl}/user/${userId}`);
  }

  createBehavior(request: BehaviorRequest): Observable<Behavior> {
    return this.http.post<Behavior>(this.behaviorApiUrl, request);
  }

  updateBehavior(id: string, request: BehaviorRequest): Observable<Behavior> {
    return this.http.put<Behavior>(`${this.behaviorApiUrl}/${id}`, request);
  }

  deleteBehavior(id: string): Observable<void> {
    return this.http.delete<void>(`${this.behaviorApiUrl}/${id}`);
  }
}
