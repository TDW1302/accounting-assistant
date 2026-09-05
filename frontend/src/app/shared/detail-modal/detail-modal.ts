import { Component, HostListener, input, output } from '@angular/core';

/**
 * Coquille des fenetres de detail ouvertes au double-clic sur une ligne de
 * liste: elle porte le fond, l'en-tete et le bouton Fermer, chaque entite
 * n'ayant plus qu'a projeter ses sections et ses actions propres.
 */
@Component({
  selector: 'app-detail-modal',
  templateUrl: './detail-modal.html'
})
export class DetailModal {
  readonly title = input.required<string>();
  readonly closed = output<void>();

  /** Echap ferme le detail: c'est le reflexe attendu d'une fenetre modale. */
  @HostListener('document:keydown.escape')
  close(): void {
    this.closed.emit();
  }
}
