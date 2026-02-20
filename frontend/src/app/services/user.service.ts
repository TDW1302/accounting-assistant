import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { User, UserRequest, UserUpdateRequest } from '../models/user.model';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/users';

  list(): Observable<User[]> {
    return this.http.get<User[]>(this.url);
  }

  get(id: number): Observable<User> {
    return this.http.get<User>(`${this.url}/${id}`);
  }

  create(request: UserRequest): Observable<User> {
    return this.http.post<User>(this.url, request);
  }

  update(id: number, request: UserUpdateRequest): Observable<User> {
    return this.http.put<User>(`${this.url}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
