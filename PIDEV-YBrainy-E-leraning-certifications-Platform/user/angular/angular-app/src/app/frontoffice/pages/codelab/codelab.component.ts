import { Component, OnInit } from '@angular/core';

type CodeLabTheme = 'dark' | 'light';
type CodeLabSection = 'editor' | 'projects' | 'stats';
type CodeLabLanguage = 'javascript' | 'python' | 'html' | 'css' | 'typescript' | 'java';

interface CodeLabLanguageOption {
  id: CodeLabLanguage;
  label: string;
  accent: string;
  fileName: string;
  starter: string;
}

interface SavedCodeLabProject {
  id: string;
  name: string;
  language: CodeLabLanguage;
  code: string;
  updatedAt: string;
}

interface CodeLabStats {
  executions: number;
  errors: number;
  languageUsage: Record<CodeLabLanguage, number>;
}

interface PyodideWindow extends Window {
  loadPyodide?: (options: { indexURL: string }) => Promise<PyodideApi>;
}

interface PyodideApi {
  loadPackage(packages: string | string[]): Promise<void>;
  runPythonAsync(code: string): Promise<unknown>;
  setStdout(options: { batched?: (message: string) => void }): void;
  setStderr(options: { batched?: (message: string) => void }): void;
}

@Component({
  selector: 'app-codelab',
  standalone: false,
  templateUrl: './codelab.component.html',
  styleUrls: ['./codelab.component.css'],
  host: { style: 'display:block' }
})
export class CodeLabComponent implements OnInit {
  private static readonly projectsStorageKey = 'frontoffice-codelab-projects';
  private static readonly statsStorageKey = 'frontoffice-codelab-stats';
  private static readonly themeStorageKey = 'frontoffice-codelab-theme';

  readonly languages: CodeLabLanguageOption[] = [
    {
      id: 'javascript',
      label: 'JavaScript',
      accent: '#f7df1e',
      fileName: 'script.js',
      starter:
        "// Welcome to CodeStudio - JavaScript\n" +
        "// Try running this example.\n\n" +
        "function fibonacci(n) {\n" +
        "  if (n <= 1) return n;\n" +
        "  return fibonacci(n - 1) + fibonacci(n - 2);\n" +
        "}\n\n" +
        "console.log('Fibonacci sequence:');\n" +
        "for (let i = 0; i < 8; i += 1) {\n" +
        "  console.log(`F(${i}) = ${fibonacci(i)}`);\n" +
        "}\n"
    },
    {
      id: 'python',
      label: 'Python',
      accent: '#5b9bd5',
      fileName: 'main.py',
      starter:
        "# Welcome to CodeStudio - Python\n" +
        "# This trainer view can help you practice syntax.\n\n" +
        "def greet(name):\n" +
        "    return f\"Hello, {name}!\"\n\n" +
        "print(greet(\"Learner\"))\n"
    },
    {
      id: 'html',
      label: 'HTML',
      accent: '#ff6b35',
      fileName: 'index.html',
      starter:
        "<section class=\"card\">\n" +
        "  <h1>Build your idea</h1>\n" +
        "  <p>Edit the markup and run to preview it.</p>\n" +
        "</section>\n"
    },
    {
      id: 'css',
      label: 'CSS',
      accent: '#2f80ed',
      fileName: 'styles.css',
      starter:
        "body {\n" +
        "  font-family: 'Segoe UI', sans-serif;\n" +
        "  background: linear-gradient(135deg, #f6f8ff, #eef9f6);\n" +
        "  color: #1f2747;\n" +
        "}\n\n" +
        ".card {\n" +
        "  max-width: 420px;\n" +
        "  margin: 40px auto;\n" +
        "  padding: 24px;\n" +
        "  border-radius: 24px;\n" +
        "  background: white;\n" +
        "  box-shadow: 0 20px 40px rgba(36, 56, 122, 0.12);\n" +
        "}\n"
    },
    {
      id: 'typescript',
      label: 'TypeScript',
      accent: '#4f8dff',
      fileName: 'main.ts',
      starter:
        "type Learner = {\n" +
        "  name: string;\n" +
        "  score: number;\n" +
        "};\n\n" +
        "const learner: Learner = { name: 'YBrainy', score: 98 };\n" +
        "console.log(`${learner.name} scored ${learner.score}`);\n"
    },
    {
      id: 'java',
      label: 'Java',
      accent: '#f39c12',
      fileName: 'Main.java',
      starter:
        "public class Main {\n" +
        "  public static void main(String[] args) {\n" +
        "    System.out.println(\"Welcome to CodeStudio Java practice\");\n" +
        "  }\n" +
        "}\n"
    }
  ];

  activeTheme: CodeLabTheme = 'dark';
  activeSection: CodeLabSection = 'editor';
  activeLanguage: CodeLabLanguage = 'javascript';
  code = '';
  consoleOutput = 'Click Run to see the result.';
  previewDocument = '';
  projectName = 'My First Project';
  savedProjects: SavedCodeLabProject[] = [];
  stats: CodeLabStats = this.createEmptyStats();
  lineCount = 1;
  columnCount = 1;
  lastSavedAt = '';
  isRunning = false;
  pythonRuntimeReady = false;
  pythonRuntimeLoading = false;
  private pyodide: PyodideApi | null = null;
  private pyodideLoaderPromise: Promise<PyodideApi> | null = null;

  ngOnInit(): void {
    this.savedProjects = this.readProjects();
    this.stats = this.readStats();
    this.activeTheme = this.readTheme();
    this.loadLanguage(this.activeLanguage);
  }

  get activeLanguageMeta(): CodeLabLanguageOption {
    return this.languages.find((language) => language.id === this.activeLanguage) ?? this.languages[0];
  }

  get lineNumbers(): number[] {
    return Array.from({ length: this.lineCount }, (_, index) => index + 1);
  }

  get totalLinesOfCode(): number {
    return this.savedProjects.reduce((total, project) => total + this.countLines(project.code), 0);
  }

  get projectCount(): number {
    return this.savedProjects.length;
  }

  get usageEntries(): Array<{ label: string; value: number; accent: string }> {
    return this.languages.map((language) => ({
      label: language.label,
      value: this.stats.languageUsage[language.id] ?? 0,
      accent: language.accent
    }));
  }

  get maxUsage(): number {
    return Math.max(1, ...this.usageEntries.map((entry) => entry.value));
  }

  selectSection(section: CodeLabSection): void {
    this.activeSection = section;
  }

  selectLanguage(language: CodeLabLanguage): void {
    this.activeLanguage = language;
    this.loadLanguage(language);
  }

  toggleTheme(): void {
    this.activeTheme = this.activeTheme === 'dark' ? 'light' : 'dark';
    localStorage.setItem(CodeLabComponent.themeStorageKey, this.activeTheme);
  }

  onCodeInput(event: Event): void {
    const target = event.target as HTMLTextAreaElement | null;
    this.code = target?.value ?? '';
    this.lineCount = this.countLines(this.code);
    this.updateCursorPosition(target);
  }

  onCursorActivity(event: Event): void {
    const target = event.target as HTMLTextAreaElement | null;
    this.updateCursorPosition(target);
  }

  clearEditor(): void {
    this.code = '';
    this.consoleOutput = 'Editor cleared. Start writing your next solution.';
    this.previewDocument = '';
    this.lineCount = 1;
    this.columnCount = 1;
  }

  async copyCode(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.code);
      this.consoleOutput = 'Code copied to clipboard.';
    } catch {
      this.consoleOutput = 'Clipboard access is unavailable in this browser.';
    }
  }

  saveProject(): void {
    const normalizedName = this.projectName.trim() || `${this.activeLanguageMeta.label} Practice`;
    const existingIndex = this.savedProjects.findIndex(
      (project) => project.name.toLowerCase() === normalizedName.toLowerCase()
    );

    const project: SavedCodeLabProject = {
      id: existingIndex >= 0 ? this.savedProjects[existingIndex].id : this.buildProjectId(),
      name: normalizedName,
      language: this.activeLanguage,
      code: this.code,
      updatedAt: new Date().toISOString()
    };

    if (existingIndex >= 0) {
      this.savedProjects.splice(existingIndex, 1, project);
    } else {
      this.savedProjects.unshift(project);
    }

    this.savedProjects = [...this.savedProjects].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
    localStorage.setItem(CodeLabComponent.projectsStorageKey, JSON.stringify(this.savedProjects));
    this.lastSavedAt = this.formatDate(project.updatedAt);
    this.consoleOutput = `Project "${project.name}" saved successfully.`;
  }

  loadProject(project: SavedCodeLabProject): void {
    this.projectName = project.name;
    this.activeLanguage = project.language;
    this.code = project.code;
    this.lineCount = this.countLines(project.code);
    this.columnCount = 1;
    this.activeSection = 'editor';
    this.consoleOutput = `Loaded project "${project.name}".`;
    this.previewDocument = '';
  }

  deleteProject(projectId: string): void {
    this.savedProjects = this.savedProjects.filter((project) => project.id !== projectId);
    localStorage.setItem(CodeLabComponent.projectsStorageKey, JSON.stringify(this.savedProjects));
    this.consoleOutput = 'Project removed from your workspace.';
  }

  async runCode(): Promise<void> {
    if (this.isRunning) return;

    this.isRunning = true;
    this.previewDocument = '';
    this.stats.executions += 1;
    this.stats.languageUsage[this.activeLanguage] += 1;

    try {
      if (this.activeLanguage === 'javascript') {
        this.consoleOutput = this.executeJavaScript(this.code);
      } else if (this.activeLanguage === 'python') {
        this.consoleOutput = await this.executePython(this.code);
      } else if (this.activeLanguage === 'html') {
        this.previewDocument = this.buildHtmlPreview(this.code, '');
        this.consoleOutput = 'HTML preview rendered successfully.';
      } else if (this.activeLanguage === 'css') {
        this.previewDocument = this.buildHtmlPreview(
          '<div class="card"><h1>CSS Playground</h1><p>Your stylesheet is now previewed here.</p></div>',
          this.code
        );
        this.consoleOutput = 'CSS preview rendered successfully.';
      } else {
        this.consoleOutput = this.buildTrainerFeedback(this.activeLanguage, this.code);
      }
    } catch (error) {
      this.stats.errors += 1;
      this.consoleOutput = error instanceof Error ? error.message : 'Execution failed.';
    } finally {
      this.isRunning = false;
    }

    localStorage.setItem(CodeLabComponent.statsStorageKey, JSON.stringify(this.stats));
  }

  formatDate(value: string): string {
    return new Date(value).toLocaleString();
  }

  private loadLanguage(language: CodeLabLanguage): void {
    const current = this.languages.find((item) => item.id === language);
    this.code = current?.starter ?? '';
    this.projectName = `${current?.label ?? 'Code'} Practice`;
    this.lineCount = this.countLines(this.code);
    this.columnCount = 1;
    this.consoleOutput = `Editor ready for ${current?.label ?? 'coding'} practice.`;
    this.previewDocument = '';
  }

  private executeJavaScript(source: string): string {
    const logs: string[] = [];
    const consoleMock = {
      log: (...args: unknown[]) => logs.push(args.map((item) => this.stringifyOutput(item)).join(' ')),
      error: (...args: unknown[]) => logs.push(args.map((item) => this.stringifyOutput(item)).join(' ')),
      warn: (...args: unknown[]) => logs.push(args.map((item) => this.stringifyOutput(item)).join(' '))
    };

    const runner = new Function('console', `"use strict";\n${source}`);
    runner(consoleMock);

    return logs.length ? logs.join('\n') : 'Execution completed with no console output.';
  }

  private async executePython(source: string): Promise<string> {
    if (!source.trim()) {
      return 'Write some Python code, then click Execute.';
    }

    const pyodide = await this.ensurePyodideReady();
    const stdout: string[] = [];
    const stderr: string[] = [];

    pyodide.setStdout({
      batched: (message: string) => {
        if (message.trim()) {
          stdout.push(message);
        }
      }
    });

    pyodide.setStderr({
      batched: (message: string) => {
        if (message.trim()) {
          stderr.push(message);
        }
      }
    });

    const result = await pyodide.runPythonAsync(this.buildPythonRuntimeSource(source));

    if (stderr.length) {
      throw new Error(stderr.join('\n'));
    }

    if (stdout.length) {
      return stdout.join('\n');
    }

    if (typeof result !== 'undefined' && result !== null) {
      return this.stringifyOutput(result);
    }

    return 'Execution completed with no console output.';
  }

  private buildPythonRuntimeSource(source: string): string {
    return (
      'import builtins\n' +
      'from js import prompt\n\n' +
      'def __codelab_input(message=""):\n' +
      '    value = prompt(message if message else "Input:")\n' +
      '    if value is None:\n' +
      '        raise EOFError("Input cancelled by user.")\n' +
      '    return str(value)\n\n' +
      'builtins.input = __codelab_input\n\n' +
      source
    );
  }

  private buildTrainerFeedback(language: CodeLabLanguage, source: string): string {
    const lines = this.countLines(source);
    const feedback: string[] = [];
    feedback.push(`${this.activeLanguageMeta.label} trainer mode analyzed ${lines} lines.`);

    if (!source.trim()) {
      feedback.push('Start by writing a small function or class, then run again for guidance.');
      return feedback.join('\n');
    }

    if (language === 'python') {
      feedback.push(source.includes('def ') ? 'Nice: function syntax detected.' : 'Tip: try creating a function with `def name():`.');
      feedback.push(source.includes('print(') ? 'Output call found with `print(...)`.' : 'Tip: use `print(...)` to verify your result.');
    } else if (language === 'typescript') {
      feedback.push(source.includes('type ') || source.includes('interface ') ? 'Great: typed structure detected.' : 'Tip: define a `type` or `interface` for stronger modeling.');
      feedback.push(source.includes('const ') ? 'Immutable variable declaration detected.' : 'Tip: prefer `const` for values that do not change.');
    } else if (language === 'java') {
      feedback.push(source.includes('public class') ? 'Class declaration found.' : 'Tip: Java programs usually start with `public class Main`.');
      feedback.push(source.includes('main(String[] args)') ? 'Entry point method detected.' : 'Tip: add `public static void main(String[] args)` to run a program.');
    }

    feedback.push('This frontoffice simulator currently gives guided practice feedback for this language.');
    return feedback.join('\n');
  }

  private async ensurePyodideReady(): Promise<PyodideApi> {
    if (this.pyodide) {
      return this.pyodide;
    }

    if (!this.pyodideLoaderPromise) {
      this.pyodideLoaderPromise = this.loadPyodideRuntime();
    }

    this.pythonRuntimeLoading = true;

    try {
      this.pyodide = await this.pyodideLoaderPromise;
      this.pythonRuntimeReady = true;
      return this.pyodide;
    } finally {
      this.pythonRuntimeLoading = false;
    }
  }

  private async loadPyodideRuntime(): Promise<PyodideApi> {
    const browserWindow = window as PyodideWindow;

    if (!browserWindow.loadPyodide) {
      await new Promise<void>((resolve, reject) => {
        const existingScript = document.querySelector('script[data-pyodide-loader="true"]') as HTMLScriptElement | null;
        if (existingScript) {
          existingScript.addEventListener('load', () => resolve(), { once: true });
          existingScript.addEventListener('error', () => reject(new Error('Unable to load the Python runtime.')), {
            once: true
          });
          return;
        }

        const script = document.createElement('script');
        script.src = 'https://cdn.jsdelivr.net/pyodide/v0.26.4/full/pyodide.js';
        script.async = true;
        script.setAttribute('data-pyodide-loader', 'true');
        script.onload = () => resolve();
        script.onerror = () => reject(new Error('Unable to load the Python runtime.'));
        document.head.appendChild(script);
      });
    }

    if (!browserWindow.loadPyodide) {
      throw new Error('Python runtime is not available.');
    }

    const pyodide = await browserWindow.loadPyodide({
      indexURL: 'https://cdn.jsdelivr.net/pyodide/v0.26.4/full/'
    });

    await pyodide.loadPackage('micropip');
    return pyodide;
  }

  private buildHtmlPreview(markup: string, styles: string): string {
    return (
      '<!doctype html><html><head><meta charset="utf-8" />' +
      '<meta name="viewport" content="width=device-width, initial-scale=1" />' +
      `<style>body{font-family:Segoe UI,sans-serif;padding:24px;background:#f6f8ff;color:#1f2747;}${styles}</style>` +
      '</head><body>' +
      markup +
      '</body></html>'
    );
  }

  private stringifyOutput(value: unknown): string {
    if (typeof value === 'string') return value;
    try {
      return JSON.stringify(value);
    } catch {
      return String(value);
    }
  }

  private countLines(content: string): number {
    return Math.max(1, content.split('\n').length);
  }

  private updateCursorPosition(textarea: HTMLTextAreaElement | null): void {
    if (!textarea) return;
    const position = textarea.selectionStart ?? 0;
    const content = textarea.value.slice(0, position);
    const rows = content.split('\n');
    this.lineCount = this.countLines(textarea.value);
    this.columnCount = (rows[rows.length - 1]?.length ?? 0) + 1;
  }

  private readProjects(): SavedCodeLabProject[] {
    try {
      const raw = localStorage.getItem(CodeLabComponent.projectsStorageKey);
      return raw ? (JSON.parse(raw) as SavedCodeLabProject[]) : [];
    } catch {
      return [];
    }
  }

  private readStats(): CodeLabStats {
    try {
      const raw = localStorage.getItem(CodeLabComponent.statsStorageKey);
      if (!raw) return this.createEmptyStats();
      const parsed = JSON.parse(raw) as Partial<CodeLabStats>;
      return {
        executions: parsed.executions ?? 0,
        errors: parsed.errors ?? 0,
        languageUsage: {
          javascript: parsed.languageUsage?.javascript ?? 0,
          python: parsed.languageUsage?.python ?? 0,
          html: parsed.languageUsage?.html ?? 0,
          css: parsed.languageUsage?.css ?? 0,
          typescript: parsed.languageUsage?.typescript ?? 0,
          java: parsed.languageUsage?.java ?? 0
        }
      };
    } catch {
      return this.createEmptyStats();
    }
  }

  private readTheme(): CodeLabTheme {
    const stored = localStorage.getItem(CodeLabComponent.themeStorageKey);
    return stored === 'light' ? 'light' : 'dark';
  }

  private createEmptyStats(): CodeLabStats {
    return {
      executions: 0,
      errors: 0,
      languageUsage: {
        javascript: 0,
        python: 0,
        html: 0,
        css: 0,
        typescript: 0,
        java: 0
      }
    };
  }

  private buildProjectId(): string {
    return `project-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  }
}
