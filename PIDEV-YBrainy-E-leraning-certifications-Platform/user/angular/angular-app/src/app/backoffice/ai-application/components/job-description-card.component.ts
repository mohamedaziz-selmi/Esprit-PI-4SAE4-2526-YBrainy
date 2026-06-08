import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'job-description-card',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './job-description-card.component.html',
  styleUrl: './job-description-card.component.css',
})
export class JobDescriptionCardComponent {
  @Input() title = 'Step 2. Job Description';
  @Input() value = '';
  @Output() valueChange = new EventEmitter<string>();
}
