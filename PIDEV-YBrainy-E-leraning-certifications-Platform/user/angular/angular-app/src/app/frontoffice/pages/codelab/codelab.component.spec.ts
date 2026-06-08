import { CommonModule } from '@angular/common';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CodeLabComponent } from './codelab.component';

describe('CodeLabComponent', () => {
  let component: CodeLabComponent;
  let fixture: ComponentFixture<CodeLabComponent>;

  beforeEach(async () => {
    localStorage.clear();

    await TestBed.configureTestingModule({
      declarations: [CodeLabComponent],
      imports: [CommonModule]
    }).compileComponents();

    fixture = TestBed.createComponent(CodeLabComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });
  
  afterEach(() => {
    localStorage.clear();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
    expect(component.activeLanguage).toBe('javascript');
  });

  it('should load the selected language starter when switching tabs', () => {
    component.selectLanguage('python');

    expect(component.activeLanguage).toBe('python');
    expect(component.projectName).toBe('Python Practice');
    expect(component.code).toContain('def greet(name):');
  });

  it('should execute JavaScript and print console output', async () => {
    component.activeLanguage = 'javascript';
    component.code = "console.log('hello test')";

    await component.runCode();

    expect(component.consoleOutput).toContain('hello test');
    expect(component.stats.executions).toBe(1);
  });

  it('should execute Python through the mocked runtime and wrap input support', async () => {
    let stdoutHandler: ((message: string) => void) | undefined;
    let stderrHandler: ((message: string) => void) | undefined;

    const runPythonAsync = jasmine.createSpy('runPythonAsync').and.callFake(async (source: string) => {
      expect(source).toContain('builtins.input = __codelab_input');
      stdoutHandler?.('30');
      return null;
    });

    const fakePyodide = {
      loadPackage: async () => undefined,
      setStdout: ({ batched }: { batched?: (message: string) => void }) => {
        stdoutHandler = batched;
      },
      setStderr: ({ batched }: { batched?: (message: string) => void }) => {
        stderrHandler = batched;
      },
      runPythonAsync
    };

    spyOn<any>(component, 'ensurePyodideReady').and.resolveTo(fakePyodide);

    component.activeLanguage = 'python';
    component.code = 'x = int(input("x"))\nprint(x * 2)';

    await component.runCode();

    expect(stderrHandler).toBeDefined();
    expect(component.consoleOutput).toBe('30');
    expect(runPythonAsync).toHaveBeenCalled();
  });

  it('should save and list a project in local storage', () => {
    component.projectName = 'Loop Practice';
    component.activeLanguage = 'javascript';
    component.code = 'for (let i = 0; i < 3; i += 1) console.log(i);';

    component.saveProject();

    expect(component.savedProjects.length).toBe(1);
    expect(component.savedProjects[0].name).toBe('Loop Practice');
    expect(localStorage.getItem('frontoffice-codelab-projects')).toContain('Loop Practice');
  });
});
