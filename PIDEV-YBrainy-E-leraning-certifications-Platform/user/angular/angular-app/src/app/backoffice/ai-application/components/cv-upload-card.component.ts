import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'cv-upload-card',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './cv-upload-card.component.html',
  styleUrl: './cv-upload-card.component.css',
})
export class CvUploadCardComponent {
  private static readonly MAX_FILE_BYTES = 10_000_000;
  private static readonly MAX_CV_CHARS = 1_000_000;

  @Input() title = 'Step 1. CV Upload';
  @Input() cvText = '';
  @Input() fileName: string | null = null;

  @Output() cvChange = new EventEmitter<{ text: string; fileName: string | null }>();

  isDragOver = false;
  errorMessage = '';

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.isDragOver = true;
  }

  onDragLeave(): void {
    this.isDragOver = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.isDragOver = false;
    const file = event.dataTransfer?.files?.item(0);
    if (!file) return;
    this.readFile(file);
  }

  onFilePick(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.item(0);
    if (!file) return;
    this.readFile(file);
    input.value = '';
  }

  onTextInput(value: string): void {
    if (value.length > CvUploadCardComponent.MAX_CV_CHARS) {
      this.errorMessage = `CV text is too long. Keep it below ${CvUploadCardComponent.MAX_CV_CHARS} characters.`;
      return;
    }
    this.errorMessage = '';
    this.cvChange.emit({ text: value, fileName: this.fileName });
  }

  private readFile(file: File): void {
    if (file.size > CvUploadCardComponent.MAX_FILE_BYTES) {
      this.errorMessage = `File too large. Max size is ${Math.round(
        CvUploadCardComponent.MAX_FILE_BYTES / 1_000_000
      )}MB.`;
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      const text = typeof reader.result === 'string' ? reader.result.trim() : '';
      if (!text) {
        this.errorMessage = 'Unable to extract text from this file. Paste the CV manually below.';
        return;
      }
      if (text.length > CvUploadCardComponent.MAX_CV_CHARS) {
        this.errorMessage = `Extracted CV text is too long. Max is ${CvUploadCardComponent.MAX_CV_CHARS} characters.`;
        return;
      }
      this.errorMessage = '';
      this.cvChange.emit({ text, fileName: file.name });
    };
    reader.onerror = () => {
      this.errorMessage = 'File reading failed. Try another file or paste manually.';
    };
    reader.readAsText(file);
  }
}
