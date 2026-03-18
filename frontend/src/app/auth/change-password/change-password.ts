import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { AiProvider } from '../../models/user.model';

@Component({
  selector: 'app-change-password',
  imports: [FormsModule],
  templateUrl: './change-password.html',
  styleUrl: './change-password.scss'
})
export class ChangePassword implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  currentPassword = '';
  newPassword = '';
  error = '';
  success = false;

  selectedProvider: AiProvider = 'CLAUDE';
  providerError = '';
  providerSaved = false;

  get isExpired(): boolean {
    return this.authService.passwordExpired();
  }

  ngOnInit() {
    const user = this.authService.currentUser();
    if (user) {
      this.selectedProvider = user.aiProvider || 'CLAUDE';
    }
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

  saveProvider() {
    this.providerError = '';
    this.providerSaved = false;
    this.authService.updateAiProvider({ aiProvider: this.selectedProvider }).subscribe({
      next: () => {
        this.providerSaved = true;
      },
      error: err => {
        this.providerError = err.error?.error || 'Erreur lors de la sauvegarde';
      }
    });
  }
}
