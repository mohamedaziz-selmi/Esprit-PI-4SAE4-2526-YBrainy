import { ErrorHandler, Injectable } from '@angular/core';
import { environment } from '../environments/environment';

function stringifyError(error: unknown): string {
  if (error instanceof Error) {
    return `${error.name}: ${error.message}\n${error.stack ?? ''}`.trim();
  }

  if (typeof error === 'string') {
    return error;
  }

  try {
    return JSON.stringify(error, null, 2);
  } catch {
    return String(error);
  }
}

function renderRuntimeError(error: unknown): void {
  if (typeof document === 'undefined' || environment.production) {
    return;
  }

  const id = 'ybrainy-runtime-error';
  let host = document.getElementById(id);
  if (!host) {
    host = document.createElement('pre');
    host.id = id;
    host.style.position = 'fixed';
    host.style.top = '16px';
    host.style.left = '16px';
    host.style.right = '16px';
    host.style.zIndex = '2147483647';
    host.style.maxHeight = '50vh';
    host.style.overflow = 'auto';
    host.style.padding = '16px';
    host.style.margin = '0';
    host.style.whiteSpace = 'pre-wrap';
    host.style.background = '#1f2937';
    host.style.color = '#f9fafb';
    host.style.border = '1px solid #ef4444';
    host.style.borderRadius = '12px';
    host.style.boxShadow = '0 20px 40px rgba(0, 0, 0, 0.35)';
    document.body.appendChild(host);
  }

  host.textContent = `Runtime error detected\n\n${stringifyError(error)}`;
}

@Injectable()
export class RuntimeErrorHandler implements ErrorHandler {
  handleError(error: unknown): void {
    renderRuntimeError(error);
    // Keep the original error visible in the console too.
    // eslint-disable-next-line no-console
    console.error(error);
  }
}

export function installGlobalRuntimeErrorHooks(): void {
  if (typeof window === 'undefined' || environment.production) {
    return;
  }

  window.addEventListener('error', (event) => {
    renderRuntimeError(event.error ?? event.message);
  });

  window.addEventListener('unhandledrejection', (event) => {
    renderRuntimeError(event.reason);
  });
}
