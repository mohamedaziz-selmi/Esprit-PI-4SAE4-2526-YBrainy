import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';

export type ResultTab = 'optimizedCV' | 'coverLetter';

@Component({
  selector: 'result-tabs',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './result-tabs.component.html',
  styleUrl: './result-tabs.component.css',
})
export class ResultTabsComponent {
  @Input() activeTab: ResultTab = 'optimizedCV';
  @Input() atsScore: number | null = null;

  @Output() tabChange = new EventEmitter<ResultTab>();

  setTab(tab: ResultTab): void {
    this.tabChange.emit(tab);
  }
}
