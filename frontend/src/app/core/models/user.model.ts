import { Role } from './auth.model';

/**
 * Represents the authenticated user stored in memory
 */
export interface User {
  email: string;
  fullName: string;
  role: Role;
}