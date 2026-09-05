import { Component, computed, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { User, UserRole } from '../../models/user.model';
import { DetailModal } from '../../shared/detail-modal/detail-modal';

const ROLE_LABELS: Record<UserRole, string> = {
  ADMIN: 'Admin',
  USER: 'Utilisateur',
  VIEWER: 'Lecteur',
};

@Component({
  selector: 'app-user-detail',
  imports: [RouterLink, DatePipe, DetailModal],
  templateUrl: './user-detail.html'
})
export class UserDetail {
  readonly user = input.required<User>();
  readonly closed = output<void>();

  readonly roleLabel = computed(() => ROLE_LABELS[this.user().role]);
}
