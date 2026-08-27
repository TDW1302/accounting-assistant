import { Component, inject, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { UserService } from '../../services/user.service';
import { UserRole } from '../../models/user.model';
import {
  PASSWORD_HINT,
  PASSWORD_MAX_LENGTH,
  PASSWORD_MIN_LENGTH,
  PASSWORD_PATTERN
} from '../../models/password-policy';

@Component({
  selector: 'app-user-form',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './user-form.html',
  styleUrl: './user-form.scss'
})
export class UserForm implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly userService = inject(UserService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  isEdit = false;
  userId: number | null = null;
  error = '';
  readonly passwordHint = PASSWORD_HINT;

  form = this.fb.group({
    username: ['', [Validators.required, Validators.minLength(3)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [
      Validators.required,
      Validators.minLength(PASSWORD_MIN_LENGTH),
      Validators.maxLength(PASSWORD_MAX_LENGTH),
      Validators.pattern(PASSWORD_PATTERN)
    ]],
    role: ['VIEWER', Validators.required],
    enabled: [true, Validators.required]
  });

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEdit = true;
      this.userId = +id;
      this.form.get('username')!.disable();
      this.form.get('password')!.clearValidators();
      this.form.get('password')!.disable();
      this.userService.get(this.userId).subscribe(user => {
        this.form.patchValue({
          username: user.username,
          email: user.email,
          role: user.role,
          enabled: user.enabled
        });
      });
    }
  }

  save() {
    this.error = '';
    if (this.isEdit && this.userId) {
      const { email, role, enabled } = this.form.getRawValue();
      this.userService.update(this.userId, {
        email: email!,
        role: role! as UserRole,
        enabled: enabled!
      }).subscribe({
        next: () => this.router.navigate(['/users']),
        error: err => this.error = this.messageOf(err)
      });
    } else {
      const { username, email, password, role, enabled } = this.form.value;
      this.userService.create({
        username: username!,
        email: email!,
        password: password!,
        role: role! as UserRole,
        enabled: enabled!
      }).subscribe({
        next: () => this.router.navigate(['/users']),
        error: err => this.error = this.messageOf(err)
      });
    }
  }

  /** L'API rend soit {error}, soit {errors: {champ: message}} en cas de validation. */
  private messageOf(err: unknown): string {
    const body = (err as { error?: { error?: string; errors?: Record<string, string> } }).error;
    if (body?.error) return body.error;
    if (body?.errors) return Object.values(body.errors).join(' ');
    return 'Erreur lors de l\'enregistrement';
  }
}
