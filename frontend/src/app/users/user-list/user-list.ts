import { Component, inject, signal, OnInit } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
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
  private readonly router = inject(Router);

  users = signal<User[]>([]);

  ngOnInit() {
    this.load();
  }

  load() {
    this.userService.list().subscribe(users => this.users.set(users));
  }

  /** Double-clic sur une ligne: meme destination que son bouton Modifier. */
  openUser(user: User) {
    this.router.navigate(['/users', user.id, 'edit']);
  }

  deleteUser(user: User) {
    if (confirm(`Supprimer l'utilisateur "${user.username}" ?`)) {
      this.userService.delete(user.id).subscribe({
        next: () => this.load(),
        error: () => alert('Erreur lors de la suppression de l\'utilisateur.')
      });
    }
  }
}
