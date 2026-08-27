import { Component, inject, OnInit, AfterViewInit, signal, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { SidebarComponent } from '../../../../shared/components/sidebar/sidebar';
import { DashboardService, DashboardStats } from '../../../../core/services/dashboard.service';
import { TranslationService } from '../../../../core/services/translation.service';
import { AuthService } from '../../../../core/services/auth';
import { TicketService } from '../../../../core/services/ticket.service';
import { TicketStatus } from '../../../../core/models/ticket.model';
import { Chart, registerables } from 'chart.js';
import { forkJoin } from 'rxjs';

Chart.register(...registerables);

/**
 * Admin dashboard component.
 * Displays platform statistics fetched from backend services,
 * including three Chart.js visualizations (roles, documents, tickets).
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    SidebarComponent
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss'
})
export class DashboardComponent implements OnInit, AfterViewInit {

  private dashboardService = inject(DashboardService);
  private ticketService = inject(TicketService);
  ts = inject(TranslationService);
  authService = inject(AuthService);

  stats = signal<DashboardStats | null>(null);
  ticketCounts = signal<{ open: number; inProgress: number; closed: number } | null>(null);
  isLoading = signal(true);
  error = signal('');

  @ViewChild('rolesChart') rolesChartRef!: ElementRef<HTMLCanvasElement>;
  @ViewChild('docsChart') docsChartRef!: ElementRef<HTMLCanvasElement>;
  @ViewChild('ticketsChart') ticketsChartRef!: ElementRef<HTMLCanvasElement>;

  private rolesChart: Chart | null = null;
  private docsChart: Chart | null = null;
  private ticketsChart: Chart | null = null;
  private viewReady = false;

  /** Quick action cards for admin navigation */
  quickActions: Array<{
    icon: string;
    label: string;
    route: string;
    color: string;
  }> = [];

  ngOnInit(): void {
    this.buildQuickActions();
    this.loadStats();
  }

  ngAfterViewInit(): void {
    this.viewReady = true;
    if (this.stats() && this.ticketCounts()) {
      this.tryRenderCharts();
    }
  }

  /**
   * Builds quick action cards with translated labels.
   */
  buildQuickActions(): void {
    this.quickActions = [
      {
        icon: 'folder',
        label: this.ts.t().nav_documents,
        route: '/admin/documents',
        color: '#e94560'
      },
      {
        icon: 'group',
        label: this.ts.t().nav_users,
        route: '/admin/users',
        color: '#0f3460'
      }
    ];
  }

  /**
   * Fetches platform stats (users/documents) and tickets in parallel.
   * Ticket counts by status are aggregated client-side from the ticket
   * list, since there's no dedicated /stats endpoint on chat-service.
   */
  loadStats(): void {
    this.isLoading.set(true);
    this.error.set('');

    forkJoin({
      stats: this.dashboardService.getStats(),
      tickets: this.ticketService.getTickets()
    }).subscribe({
      next: ({ stats, tickets }) => {
        this.stats.set(stats);

        this.ticketCounts.set({
          open: tickets.filter(t => t.status === TicketStatus.OPEN).length,
          inProgress: tickets.filter(t => t.status === TicketStatus.IN_PROGRESS).length,
          closed: tickets.filter(t => t.status === TicketStatus.CLOSED).length
        });

        this.isLoading.set(false);
        this.tryRenderCharts();
      },
      error: () => {
        this.error.set(this.ts.t().common_error);
        this.isLoading.set(false);
      }
    });
  }

  /**
   * Attempts to render the charts, retrying shortly after if the canvas
   * elements aren't yet in the DOM (Angular needs a tick to render the
   * @if block after isLoading/stats change).
   */
  private tryRenderCharts(): void {
    if (!this.viewReady || !this.stats() || !this.ticketCounts()) return;

    setTimeout(() => {
      const ready = this.rolesChartRef?.nativeElement
        && this.docsChartRef?.nativeElement
        && this.ticketsChartRef?.nativeElement;

      if (ready) {
        this.renderCharts();
      } else {
        // Un seul retry supplémentaire au cas où le DOM n'est pas encore prêt
        setTimeout(() => this.renderCharts(), 150);
      }
    }, 0);
  }

  /**
   * Creates or refreshes the three charts with the current stats.
   * Destroys previous instances first to avoid memory leaks on refresh.
   */
  private renderCharts(): void {
    const stats = this.stats();
    const tickets = this.ticketCounts();
    if (!stats || !tickets) return;

    this.rolesChart?.destroy();
    this.docsChart?.destroy();
    this.ticketsChart?.destroy();

    if (this.rolesChartRef?.nativeElement) {
      this.rolesChart = new Chart(this.rolesChartRef.nativeElement, {
        type: 'doughnut',
        data: {
          labels: ['Admins', 'Experts', 'Opérationnels'],
          datasets: [{
            data: [stats.adminCount, stats.expertCount, stats.operationnelCount],
            backgroundColor: ['#e94560', '#0f3460', '#f57c00'],
            borderWidth: 0
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: {
            legend: {
              position: 'bottom',
              labels: { font: { size: 12 }, padding: 16 }
            }
          }
        }
      });
    }

    if (this.docsChartRef?.nativeElement) {
      this.docsChart = new Chart(this.docsChartRef.nativeElement, {
        type: 'bar',
        data: {
          labels: ['Total', 'Actifs', 'Archivés', 'Indexés'],
          datasets: [{
            label: 'Documents',
            data: [stats.totalDocuments, stats.activeDocuments, stats.archivedDocuments, stats.indexedDocuments],
            backgroundColor: ['#0f3460', '#2e7d32', '#c62828', '#e94560'],
            borderRadius: 6
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: { legend: { display: false } },
          scales: {
            y: { beginAtZero: true, ticks: { precision: 0 } }
          }
        }
      });
    }

    if (this.ticketsChartRef?.nativeElement) {
      this.ticketsChart = new Chart(this.ticketsChartRef.nativeElement, {
        type: 'doughnut',
        data: {
          labels: ['Ouvert', 'En cours', 'Clôturé'],
          datasets: [{
            data: [tickets.open, tickets.inProgress, tickets.closed],
            backgroundColor: [
              this.ticketService.getStatusColor(TicketStatus.OPEN),
              this.ticketService.getStatusColor(TicketStatus.IN_PROGRESS),
              this.ticketService.getStatusColor(TicketStatus.CLOSED)
            ],
            borderWidth: 0
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: {
            legend: {
              position: 'bottom',
              labels: { font: { size: 12 }, padding: 16 }
            }
          }
        }
      });
    }
  }
}