import { Component, input } from '@angular/core';

/**
 * Yellow header under the customer toolbar (docs/07 §13.1).
 * "tall" is for the dashboard: account cards overlap its bottom by 48px.
 * "compact" is for other pages: a form card overlaps it by 28px.
 * The page puts its own content inside.
 */
@Component({
  selector: 'app-page-hero',
  template: '<ng-content />',
  styleUrl: './page-hero.scss',
  host: { '[class.tall]': "size() === 'tall'" },
})
export class PageHero {
  readonly size = input<'tall' | 'compact'>('compact');
}
