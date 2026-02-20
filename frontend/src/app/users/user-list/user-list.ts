import { Component, inject, signal, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { User } from '../../models/user.model';
import { UserService } from '../../services/user.service';

@Component({
  selector: 'app-user-list',
  imports: [RouterLink, DatePipe],
  templateUrl: './user-list.html',
  styleUrl: './user-list.scss'
})
export class UserList implements OnInit {
  private readonly userService = inject(UserService);

  users = signal<User[]>([]);

  ngOnInit() {
    this.load();
  }

  load() {
    this.userService.list().subscribe(users => this.users.set(users));
  }

  deleteUser(user: User) {
    if (confirm(`Supprimer l'utilisateur "${user.username}" ?`)) {
      this.userService.delete(user.id).subscribe(() => this.load());
    }
  }
}
