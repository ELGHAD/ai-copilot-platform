import { Injectable, signal } from '@angular/core';

/**
 * Wrapper autour de l'API Web Speech du navigateur (SpeechRecognition).
 * Fonctionne 100% côté client, aucun appel réseau/backend.
 * Supporté nativement sur Chrome/Edge.
 */
@Injectable({
  providedIn: 'root'
})
export class SpeechRecognitionService {

  isListening = signal(false);
  isSupported = signal(this.checkSupport());

  private recognition: any = null;

  private checkSupport(): boolean {
    return !!(window as any).SpeechRecognition || !!(window as any).webkitSpeechRecognition;
  }

  /**
   * Démarre l'écoute du micro.
   * @param lang locale à utiliser (ex: 'fr-FR', 'en-US')
   * @param onResult callback appelé à chaque transcription (interim ou finale)
   * @param onError callback appelé en cas d'erreur (permission refusée, etc.)
   */
  start(
    lang: string,
    onResult: (transcript: string, isFinal: boolean) => void,
    onError?: (error: string) => void
  ): void {
    if (!this.isSupported() || this.isListening()) return;

    const SpeechRecognitionCtor =
      (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;

    this.recognition = new SpeechRecognitionCtor();
    this.recognition.lang = lang;
    this.recognition.continuous = true;
    this.recognition.interimResults = true;

    this.recognition.onresult = (event: any) => {
      let interim = '';
      let final = '';

      for (let i = event.resultIndex; i < event.results.length; i++) {
        const transcript = event.results[i][0].transcript;
        if (event.results[i].isFinal) {
          final += transcript;
        } else {
          interim += transcript;
        }
      }

      if (final) onResult(final, true);
      else if (interim) onResult(interim, false);
    };

    this.recognition.onerror = (event: any) => {
      this.isListening.set(false);
      onError?.(event.error);
    };

    this.recognition.onend = () => {
      this.isListening.set(false);
    };

    this.recognition.start();
    this.isListening.set(true);
  }

  /**
   * Arrête l'écoute manuellement (re-clic sur le micro).
   */
  stop(): void {
    if (this.recognition && this.isListening()) {
      this.recognition.stop();
    }
    this.isListening.set(false);
  }
}