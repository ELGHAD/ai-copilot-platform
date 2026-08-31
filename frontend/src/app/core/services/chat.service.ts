import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface SourceReference {
  documentTitle: string;
  page: number;
  excerpt: string;
  score: number;
}

export interface ChatResponse {
  conversationId: number;
  messageId: number;
  answer: string;
  sources: SourceReference[];
  confidenceScore: number;
  createdAt: string;
}

export interface MessageResponse {
  id: number;
  role: 'USER' | 'ASSISTANT';
  content: string;
  sources: SourceReference[];
  confidenceScore: number;
  createdAt: string;
}

export interface ConversationSummary {
  id: number;
  title: string;
  userEmail: string;
  userRole: string;
  messageCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface ChatRequest {
  question: string;
  conversationId?: number;
}

/**
 * Service that communicates with chat-service backend.
 * Handles sending questions and managing conversation history.
 */
@Injectable({
  providedIn: 'root'
})
export class ChatService {

  private http = inject(HttpClient);
  private readonly BASE_URL = `${environment.apiBaseUrl}/api/chat`;

  /**
   * Sends a question to the RAG pipeline.
   *
   * @param request chat request with question and optional conversationId
   * @returns Observable of ChatResponse
   */
  ask(request: ChatRequest): Observable<ChatResponse> {
    return this.http.post<ChatResponse>(`${this.BASE_URL}/ask`, request);
  }

  /**
   * Fetches all conversations for the current user.
   *
   * @returns Observable of ConversationSummary array
   */
  getConversations(): Observable<ConversationSummary[]> {
    return this.http.get<ConversationSummary[]>(`${this.BASE_URL}/conversations`);
  }

  /**
   * Fetches all messages in a specific conversation.
   *
   * @param conversationId the conversation ID
   * @returns Observable of MessageResponse array
   */
  getMessages(conversationId: number): Observable<MessageResponse[]> {
    return this.http.get<MessageResponse[]>(
      `${this.BASE_URL}/conversations/${conversationId}/messages`
    );
  }

  /**
   * Deletes a conversation and all its messages.
   *
   * @param conversationId the conversation ID
   * @returns Observable of void
   */
  deleteConversation(conversationId: number): Observable<void> {
    return this.http.delete<void>(
      `${this.BASE_URL}/conversations/${conversationId}`
    );
  }
}