import {ChangeDetectionStrategy, Component, OnInit, computed, effect, inject, signal} from '@angular/core';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {AuthenticationService} from '@core/services/authentication/authentication.service';
import {AppError} from '@core/models/api.model';
import {ToastService} from '@core/services/toast.service';
import {TranslationService} from '@core/services/i18n';
import {TranslatePipe} from '@core/services/i18n/translate.pipe';
import {CardComponent} from '@shared/ui/card/card.component';
import {AVATAR_COLORS, AVATAR_COLOR_KEYS, AvatarColor, AvatarComponent} from '@shared/ui/avatar/avatar.component';
import {IconComponent} from '@shared/ui/icon/icon.component';
import {ButtonComponent} from '@shared/ui/button/button.component';
import {TextFieldComponent} from '@shared/ui/form-field/text-field.component';
import {PageHeaderComponent} from '@shared/ui/page-header/page-header.component';

/** Perfil: el avatar con las iniciales y el nombre y apellido de la persona. */
@Component({
  selector: 'app-profile',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    CardComponent,
    AvatarComponent,
    IconComponent,
    ButtonComponent,
    TextFieldComponent,
    PageHeaderComponent,
    TranslatePipe,
  ],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss',
})
export class ProfileComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthenticationService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslationService);

  readonly user = this.auth.user;
  readonly saving = signal(false);

  readonly colors = AVATAR_COLOR_KEYS;
  /** Color elegido; se ve al instante y se guarda en la cuenta. */
  private readonly picked = signal<AvatarColor | null>(null);
  readonly color = computed<AvatarColor>(() =>
    this.picked() ?? ((this.user()?.avatarColor as AvatarColor | null) || 'blue'));

  readonly form = this.fb.nonNullable.group({
    firstName: ['', [Validators.maxLength(100)]],
    lastName: ['', [Validators.maxLength(100)]],
  });

  constructor() {
    effect(() => {
      const u = this.user();
      if (u) {
        this.form.patchValue({firstName: u.firstName, lastName: u.lastName}, {emitEvent: false});
      }
    });
  }

  ngOnInit(): void {
    this.auth.refreshUser().subscribe({error: () => undefined});
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const {firstName, lastName} = this.form.getRawValue();
    this.auth.updateProfile({firstName, lastName}).subscribe({
      next: () => {
        this.saving.set(false);
        this.toast.success(this.i18n.translate('settings.profile.toast.updated'));
      },
      error: (err: AppError) => {
        this.saving.set(false);
        this.toast.error(err.message);
      },
    });
  }

  swatch(color: AvatarColor): string {
    return AVATAR_COLORS[color];
  }

  pickColor(color: AvatarColor): void {
    if (color === this.color()) {
      return;
    }
    const previous = this.picked();
    this.picked.set(color);
    this.auth.updatePreferences({avatarColor: color}).subscribe({
      next: () => this.picked.set(null),
      error: (err: AppError) => {
        this.picked.set(previous);
        this.toast.error(err.message);
      },
    });
  }
}
