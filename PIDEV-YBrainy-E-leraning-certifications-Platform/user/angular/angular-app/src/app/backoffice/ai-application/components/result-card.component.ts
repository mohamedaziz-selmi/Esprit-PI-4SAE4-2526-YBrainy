import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'result-card',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './result-card.component.html',
  styleUrl: './result-card.component.css',
})
export class ResultCardComponent {
  @Input({ required: true }) title = '';
  @Input() content = '';

  @Output() contentChange = new EventEmitter<string>();
  @Output() downloadClick = new EventEmitter<void>();

  copied = false;

  async copyToClipboard(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.content || '');
      this.copied = true;
      window.setTimeout(() => (this.copied = false), 1300);
    } catch {
      this.copied = false;
    }
  }
}
