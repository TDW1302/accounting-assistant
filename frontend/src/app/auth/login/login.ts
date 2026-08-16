import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { ConfigService } from '../../services/config.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  templateUrl: './login.html',
  styleUrl: './login.scss'
})
export class Login {
  private readonly authService = inject(AuthService);
  private readonly configService = inject(ConfigService);
  private readonly router = inject(Router);

  username = '';
  password = '';
  error = '';

  login() {
    this.error = '';
    this.authService.login({ username: this.username, password: this.password }).subscribe({
      next: response => {
        // /api/config n'est plus public: il se charge une fois la session ouverte.
        this.configService.loadConfig();
        if (response.passwordExpired) {
          this.router.navigate(['/change-password']);
        } else {
          this.router.navigate(['/']);
        }
      },
      error: () => {
        this.error = 'Nom d\'utilisateur ou mot de passe incorrect';
      }
    });
  }
}
