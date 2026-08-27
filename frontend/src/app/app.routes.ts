import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth-guard';
import { adminGuard } from './core/guards/admin-guard';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'auth/login',
    pathMatch: 'full'
  },
  {
    path: 'auth',
    children: [
      {
        path: 'login',
        loadComponent: () =>
          import('./features/auth/login/login').then(m => m.LoginComponent)
      },
      {
        path: 'register',
        loadComponent: () =>
          import('./features/auth/register/register').then(m => m.RegisterComponent)
      }
    ]
  },
 {
  path: 'admin',
  canActivate: [authGuard, adminGuard],
  children: [
    {
      path: 'dashboard',
      loadComponent: () =>
        import('./features/admin/dashboard/dashboard/dashboard').then(m => m.DashboardComponent)
    },
    {
      path: 'documents',
      loadComponent: () =>
        import('./features/admin/documents/documents/documents').then(m => m.DocumentsComponent)
    },
    {
      path: 'users',
      loadComponent: () =>
        import('./features/admin/users/users/users').then(m => m.UsersComponent)
    },
    {
      path: 'tickets',
      loadComponent: () =>
        import('./features/admin/tickets/tickets').then(m => m.TicketsComponent)
    }
  ]
},
  {
    path: 'chat',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/chat/chat/chat').then(m => m.ChatComponent)
  },
  {
    path: '**',
    redirectTo: 'auth/login'
  }
];