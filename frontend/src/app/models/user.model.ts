export type UserRole = 'ADMIN' | 'USER' | 'VIEWER';
export type AiProvider = 'CLAUDE' | 'GEMINI';

export interface User {
  id: number;
  username: string;
  email: string;
  role: UserRole;
  enabled: boolean;
  passwordChangedAt: string;
  passwordExpiresAt: string;
  createdAt: string;
  aiProvider: AiProvider;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface AuthResponse {
  user: User;
  passwordExpired: boolean;
}

export interface UserRequest {
  username: string;
  email: string;
  password: string;
  role: UserRole;
  enabled: boolean;
}

export interface UserUpdateRequest {
  email: string;
  role: UserRole;
  enabled: boolean;
}

export interface AiProviderRequest {
  aiProvider: AiProvider;
}
