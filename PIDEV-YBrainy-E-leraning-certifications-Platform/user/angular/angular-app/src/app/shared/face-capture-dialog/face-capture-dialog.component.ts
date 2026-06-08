import { CommonModule } from '@angular/common';
import { AfterViewInit, Component, ElementRef, EventEmitter, Input, OnDestroy, Output, ViewChild } from '@angular/core';

@Component({
  selector: 'app-face-capture-dialog',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './face-capture-dialog.component.html',
  styleUrl: './face-capture-dialog.component.css',
})
export class FaceCaptureDialogComponent implements AfterViewInit, OnDestroy {
  @Input() title = 'Face verification';
  @Input() subtitle = 'Center your face in the frame and capture a clear photo.';
  @Input() confirmLabel = 'Use photo';
  @Input() busy = false;
  @Input() continuousCapture = false;
  @Input() captureIntervalMs = 1500;
  @Input() infoMessage = '';

  @Output() canceled = new EventEmitter<void>();
  @Output() confirmed = new EventEmitter<Blob>();

  @ViewChild('videoElement') private readonly videoElement?: ElementRef<HTMLVideoElement>;
  @ViewChild('canvasElement') private readonly canvasElement?: ElementRef<HTMLCanvasElement>;

  errorMessage = '';
  previewUrl: string | null = null;

  private stream: MediaStream | null = null;
  private capturedBlob: Blob | null = null;
  private autoCaptureTimerId: number | null = null;
  private autoCaptureInProgress = false;

  async ngAfterViewInit(): Promise<void> {
    await this.startCamera();
  }

  ngOnDestroy(): void {
    this.stopAutoCaptureLoop();
    this.releasePreview();
    this.stopCamera();
  }

  close(): void {
    if (this.busy) {
      return;
    }
    this.canceled.emit();
  }

  async capture(): Promise<void> {
    if (this.busy || this.continuousCapture) {
      return;
    }

    const blob = await this.captureFrame(true);
    if (!blob) {
      return;
    }

    this.stopCamera();
    this.setCapturedBlob(blob);
  }

  async retake(): Promise<void> {
    if (this.busy) {
      return;
    }

    this.releasePreview();
    await this.startCamera();
  }

  submit(): void {
    if (this.busy || !this.capturedBlob) {
      return;
    }

    this.confirmed.emit(this.capturedBlob);
  }

  async onFileSelected(event: Event): Promise<void> {
    if (this.busy) {
      return;
    }

    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';

    if (!file) {
      return;
    }
    if (!file.type.startsWith('image/')) {
      this.errorMessage = 'Choose an image file to continue.';
      return;
    }

    if (this.continuousCapture) {
      this.errorMessage = '';
      this.confirmed.emit(file);
      return;
    }

    this.stopCamera();
    this.setCapturedBlob(file);
  }

  get statusMessage(): string {
    if (!this.continuousCapture) {
      return '';
    }

    if (this.busy) {
      return 'Checking the current frame...';
    }

    return this.infoMessage || 'Scanning every few seconds. Keep your face centered in the frame.';
  }

  private async startCamera(): Promise<void> {
    if (this.previewUrl || this.stream) {
      return;
    }

    this.errorMessage = '';

    if (!navigator.mediaDevices?.getUserMedia) {
      this.errorMessage = this.continuousCapture
        ? 'Camera access is unavailable in this browser. Use email login instead.'
        : 'Camera access is unavailable in this browser. Upload a face photo instead.';
      return;
    }

    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: {
          facingMode: 'user',
          width: { ideal: 1280 },
          height: { ideal: 720 },
        },
        audio: false,
      });

      this.stream = stream;
      const video = this.videoElement?.nativeElement;
      if (!video) {
        return;
      }

      video.srcObject = stream;
      await video.play();
      this.startAutoCaptureLoop();
    } catch (error) {
      const message =
        error instanceof DOMException && error.name === 'NotAllowedError'
          ? this.continuousCapture
            ? 'Camera permission was denied. Allow camera access or use email login instead.'
            : 'Camera permission was denied. Allow camera access or upload a photo instead.'
          : 'Could not access the camera. Check that it is connected and not in use by another app.';

      this.errorMessage = message;
      this.stopCamera();
    }
  }

  private startAutoCaptureLoop(): void {
    if (!this.continuousCapture || this.autoCaptureTimerId !== null || !this.stream) {
      return;
    }

    this.autoCaptureTimerId = window.setInterval(() => {
      void this.emitCurrentFrame();
    }, Math.max(this.captureIntervalMs, 500));
  }

  private stopAutoCaptureLoop(): void {
    if (this.autoCaptureTimerId !== null) {
      window.clearInterval(this.autoCaptureTimerId);
      this.autoCaptureTimerId = null;
    }
    this.autoCaptureInProgress = false;
  }

  private stopCamera(): void {
    this.stream?.getTracks().forEach((track) => track.stop());
    this.stream = null;

    const video = this.videoElement?.nativeElement;
    if (video) {
      video.pause();
      video.srcObject = null;
    }
  }

  private async emitCurrentFrame(): Promise<void> {
    if (
      !this.continuousCapture ||
      this.busy ||
      this.previewUrl ||
      this.autoCaptureInProgress
    ) {
      return;
    }

    this.autoCaptureInProgress = true;
    try {
      const blob = await this.captureFrame(false);
      if (blob) {
        this.confirmed.emit(blob);
      }
    } finally {
      this.autoCaptureInProgress = false;
    }
  }

  private setCapturedBlob(blob: Blob): void {
    this.releasePreview();
    this.capturedBlob = blob;
    this.previewUrl = URL.createObjectURL(blob);
    this.errorMessage = '';
  }

  private async captureFrame(showErrors: boolean): Promise<Blob | null> {
    const video = this.videoElement?.nativeElement;
    const canvas = this.canvasElement?.nativeElement;
    if (!video || !canvas || !video.videoWidth || !video.videoHeight) {
      if (showErrors) {
        this.errorMessage = 'Camera is not ready yet. Give it a second and try again.';
      }
      return null;
    }

    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;

    const context = canvas.getContext('2d');
    if (!context) {
      if (showErrors) {
        this.errorMessage = 'Could not process the captured image.';
      }
      return null;
    }

    context.drawImage(video, 0, 0, canvas.width, canvas.height);

    const blob = await new Promise<Blob | null>((resolve) => {
      canvas.toBlob(resolve, 'image/png');
    });

    if (!blob && showErrors) {
      this.errorMessage = 'Could not capture a usable image. Try again.';
    }

    return blob;
  }

  private releasePreview(): void {
    if (this.previewUrl) {
      URL.revokeObjectURL(this.previewUrl);
    }
    this.previewUrl = null;
    this.capturedBlob = null;
  }
}
