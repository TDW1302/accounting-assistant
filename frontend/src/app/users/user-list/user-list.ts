import { Component, inject, signal, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { User } from '../../models/user.model';
import { UserService } from '../../services/user.service';
import { UserDetail } from '../user-detail/user-detail';

@Component({
  selector: 'app-user-list',
  imports: [RouterLink, DatePipe, UserDetail],
  templateUrl: './user-list.html',
  styleUrl: './user-list.scss'
})
export class UserList implements OnInit {
  private readonly userService = inject(UserService);

  users = signal<User[]>([]);
  detailUser = signal<User | null>(null);

  ngOnInit() {
    this.load();
  }

  load() {
    this.userService.list().subscribe(users => this.users.set(users));
  }

  openDetail(user: User) {
    this.detailUser.set(user);
  }

  closeDetail() {
    this.detailUser.set(null);
  }

  deleteUser(user: User) {
    if (confirm(`Supprimer l'utilisateur "${user.username}" ?`)) {
      this.closeDetail();
      this.userService.delete(user.id).subscribe({
        next: () => this.load(),
        error: () => alert('Erreur lors de la suppression de l\'utilisateur.')
      });
    }
  }
}
