import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'animated-button',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './animated-button.component.html',
  styleUrl: './animated-button.component.css',
})
export class AnimatedButtonComponent {
  @Input({ required: true }) label = '';
  @Input() loading = false;
  @Input() disabled = false;
  @Input() fullWidth = false;

  @Output() pressed = new EventEmitter<void>();

  get isDisabled(): boolean {
    return this.disabled || this.loading;
  }

  onPress(): void {
    if (this.isDisabled) return;
    this.pressed.emit();
  }
}
