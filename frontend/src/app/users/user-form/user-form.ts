import { Component, inject, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { UserService } from '../../services/user.service';

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

  form = this.fb.group({
    username: ['', [Validators.required, Validators.minLength(3)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(6)]],
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
    if (this.isEdit && this.userId) {
      const { email, role, enabled } = this.form.getRawValue();
      this.userService.update(this.userId, {
        email: email!,
        role: role! as any,
        enabled: enabled!
      }).subscribe(() => this.router.navigate(['/users']));
    } else {
      const { username, email, password, role, enabled } = this.form.value;
      this.userService.create({
        username: username!,
        email: email!,
        password: password!,
        role: role! as any,
        enabled: enabled!
      }).subscribe(() => this.router.navigate(['/users']));
    }
  }
}
