/**
 * Payload sent to POST /api/auth/register
 */
export interface RegisterRequest {
  fullName: string;
  email: string;
  password: string;
  role: Role;
}

/**
 * Payload sent to POST /api/auth/login
 */
export interface LoginRequest {
  email: string;
  password: string;
}

/**
 * Response received after successful login or register
 */
export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  email: string;
  fullName: string;
  role: Role;
}

/**
 * User roles matching the backend Role enum
 */
export enum Role {
  ADMIN = 'ADMIN',
  OPERATIONNEL = 'OPERATIONNEL',
  EXPERT = 'EXPERT'
}