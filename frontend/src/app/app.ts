import { Component, inject, OnInit } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { AuthService } from './services/auth.service';
import { ConfigService } from './services/config.service';
import { APP_VERSION } from '../environments/version';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App implements OnInit {
  readonly authService = inject(AuthService);
  readonly configService = inject(ConfigService);
  private readonly router = inject(Router);
  menuOpen = false;
  version = APP_VERSION;

  ngOnInit() {
    this.configService.loadConfig();
    // 401 is expected when not logged in — silently ignore
    this.authService.getCurrentUser().subscribe({
      error: () => {}
    });
  }

  logout() {
    this.authService.logout().subscribe(() => {
      this.router.navigate(['/login']);
    });
  }
}
