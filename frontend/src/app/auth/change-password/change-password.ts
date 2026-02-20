import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-change-password',
  imports: [FormsModule],
  templateUrl: './change-password.html',
  styleUrl: './change-password.scss'
})
export class ChangePassword {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  currentPassword = '';
  newPassword = '';
  error = '';
  success = false;

  get isExpired(): boolean {
    return this.authService.passwordExpired();
  }

  changePassword() {
    this.error = '';
    this.success = false;
    this.authService.changePassword({
      currentPassword: this.currentPassword,
      newPassword: this.newPassword
    }).subscribe({
      next: () => {
        this.success = true;
        this.currentPassword = '';
        this.newPassword = '';
        setTimeout(() => this.router.navigate(['/']), 1500);
      },
      error: err => {
        this.error = err.error?.error || 'Erreur lors du changement de mot de passe';
      }
    });
  }
}
