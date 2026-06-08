import { ComponentFixture, TestBed } from '@angular/core/testing';

import { HomeComponent } from './home.component';

describe('HomeComponent', () => {
  let component: HomeComponent;
  let fixture: ComponentFixture<HomeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [HomeComponent],
    })
      .overrideComponent(HomeComponent, { set: { template: '' } })
      .compileComponents();

    // Stub the animation bootstrap so no external libs are needed
    spyOn(HomeComponent.prototype as any, 'ngAfterViewInit').and.callFake(() => {});

    fixture = TestBed.createComponent(HomeComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should expose the assets base path', () => {
    expect(component.assets).toBe('assets/frontoffice/www.ciklum.com/');
  });

  // ── Lifecycle cleanup ────────────────────────────────────────

  it('ngOnDestroy does not throw when all cleanup fields are null', () => {
    expect(() => component.ngOnDestroy()).not.toThrow();
  });

  it('ngOnDestroy calls lenis.destroy() and nullifies it', () => {
    const fakeLenis = { destroy: jasmine.createSpy('destroy') };
    (component as any).lenis = fakeLenis;

    component.ngOnDestroy();

    expect(fakeLenis.destroy).toHaveBeenCalled();
    expect((component as any).lenis).toBeNull();
  });

  it('ngOnDestroy disconnects animObserver and nullifies it', () => {
    const fakeObserver = { disconnect: jasmine.createSpy('disconnect') };
    (component as any).animObserver = fakeObserver;

    component.ngOnDestroy();

    expect(fakeObserver.disconnect).toHaveBeenCalled();
    expect((component as any).animObserver).toBeNull();
  });

  it('ngOnDestroy clears visibilityFallbackTimer', () => {
    const timerId = setTimeout(() => {}, 99999);
    (component as any).visibilityFallbackTimer = timerId;
    spyOn(window, 'clearTimeout').and.callThrough();

    component.ngOnDestroy();

    expect(window.clearTimeout).toHaveBeenCalledWith(timerId);
    expect((component as any).visibilityFallbackTimer).toBeNull();
  });

  it('ngOnDestroy calls ScrollTrigger.getAll().kill() when available', () => {
    const killSpy = jasmine.createSpy('kill');
    (window as any)['ScrollTrigger'] = {
      getAll: () => [{ kill: killSpy }],
    };

    expect(() => component.ngOnDestroy()).not.toThrow();
    expect(killSpy).toHaveBeenCalled();

    delete (window as any)['ScrollTrigger'];
  });
});
