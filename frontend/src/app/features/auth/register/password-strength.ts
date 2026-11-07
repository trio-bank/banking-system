import { Component, computed, input } from '@angular/core';
import { passwordRuleFailures } from '../../../shared/validators';

/** Strength meter + list of password rules, updated while the user types. */
@Component({
  selector: 'app-password-strength',
  template: `
    <div class="meter" [class]="'meter level-' + level()" aria-hidden="true">
      <span></span><span></span><span></span>
    </div>
    <p class="label">Password strength: {{ label() }}</p>
    <ul class="rules">
      @for (rule of rules(); track rule.label) {
        <li [class.met]="rule.met">
          <span class="icon" aria-hidden="true">{{ rule.met ? '✓' : '○' }}</span>
          <span class="status">{{ rule.met ? 'Met:' : 'Not met:' }}</span>
          {{ rule.label }}
        </li>
      }
    </ul>
  `,
  styles: `
    :host {
      display: block;
      margin-bottom: 16px;
    }

    .meter {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 4px;
    }

    .meter span {
      height: 4px;
      border-radius: 2px;
      background: var(--mat-sys-surface-container-highest);
    }

    .level-1 span:nth-child(-n + 1) {
      background: var(--mat-sys-error);
    }

    .level-2 span:nth-child(-n + 2) {
      background: var(--mat-sys-tertiary);
    }

    .level-3 span {
      background: var(--mat-sys-primary);
    }

    .label {
      margin: 8px 0;
      font: var(--mat-sys-label-large);
    }

    .rules {
      margin: 0;
      padding: 0;
      list-style: none;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-small);
    }

    li {
      display: flex;
      gap: 8px;
      padding: 2px 0;
    }

    li.met {
      color: var(--mat-sys-on-surface);
    }

    .icon {
      width: 16px;
      font-weight: 700;
    }

    /* Only for screen readers: the icon alone says nothing to them. */
    .status {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip-path: inset(50%);
    }
  `,
})
export class PasswordStrength {
  readonly password = input('');
  readonly email = input('');

  protected readonly rules = computed(() => {
    const failed = passwordRuleFailures(this.password(), this.email());
    return [
      { label: '12–128 characters', met: !failed.minLength && !failed.maxLength },
      { label: 'A lowercase letter', met: !failed.lowercase },
      { label: 'An uppercase letter', met: !failed.uppercase },
      { label: 'A digit', met: !failed.digit },
      { label: 'A special character', met: !failed.special },
      {
        label: 'Does not contain the part of your email before @',
        met: !failed.containsEmail,
      },
    ];
  });

  /** 0 = empty, 1 = weak, 2 = medium, 3 = all rules are met. */
  protected readonly level = computed(() => {
    if (!this.password()) {
      return 0;
    }
    const met = this.rules().filter((rule) => rule.met).length;
    if (met === this.rules().length) {
      return 3;
    }
    return met >= 4 ? 2 : 1;
  });

  protected readonly label = computed(() => ['—', 'weak', 'medium', 'strong'][this.level()]);
}
