import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { PageHero } from './page-hero';

@Component({
  imports: [PageHero],
  template: `<app-page-hero [size]="size()"><h1>Title</h1></app-page-hero>`,
})
class Host {
  readonly size = signal<'tall' | 'compact'>('compact');
}

describe('PageHero', () => {
  it('shows the page content and switches between compact and tall', () => {
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    const hero = (fixture.nativeElement as HTMLElement).querySelector('app-page-hero')!;

    expect(hero.querySelector('h1')?.textContent).toBe('Title');
    expect(hero.classList).not.toContain('tall');

    fixture.componentInstance.size.set('tall');
    fixture.detectChanges();
    expect(hero.classList).toContain('tall');
  });
});
