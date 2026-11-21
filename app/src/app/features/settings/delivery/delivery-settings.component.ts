import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {AppError} from '@core/models/api.model';
import {TranslationService} from '@core/services/i18n';
import {TranslatePipe} from '@core/services/i18n/translate.pipe';
import {ToastService} from '@core/services/toast.service';
import {SubscriptionService} from '@features/feed/data/subscription.service';
import {ButtonComponent} from '@shared/ui/button/button.component';
import {CardComponent} from '@shared/ui/card/card.component';
import {CheckboxComponent} from '@shared/ui/checkbox/checkbox.component';
import {SelectFieldComponent, SelectOption} from '@shared/ui/form-field/select-field.component';
import {PageHeaderComponent} from '@shared/ui/page-header/page-header.component';
import {SpinnerComponent} from '@shared/ui/spinner/spinner.component';

const COMMON_TIME_ZONES = [
  'America/Argentina/Buenos_Aires', 'America/Montevideo', 'America/Santiago', 'America/Mexico_City',
  'America/Bogota', 'America/Lima', 'America/Caracas', 'America/Sao_Paulo', 'America/New_York',
  'America/Los_Angeles', 'Europe/Madrid', 'Europe/London', 'UTC',
];

/** Cuándo y cuánto contenido recibe el usuario cada día. */
/** "America/Argentina/Buenos_Aires" → "Buenos Aires"; "UTC" queda igual. */
function zoneCity(zone: string): string {
  return zone.split('/').pop()!.replace(/_/g, ' ');
}

/** Desfase de la zona respecto de UTC en minutos, hoy (contempla el horario de verano vigente). */
function utcOffsetMinutes(zone: string): number {
  try {
    const parts = new Intl.DateTimeFormat('en-US', {timeZone: zone, timeZoneName: 'longOffset'})
      .formatToParts(new Date())
      .find((p) => p.type === 'timeZoneName')?.value ?? 'GMT';
    const match = /GMT([+-])(\d{2}):(\d{2})/.exec(parts);
    return match ? (match[1] === '-' ? -1 : 1) * (Number(match[2]) * 60 + Number(match[3])) : 0;
  } catch {
    return 0;
  }
}

function formatOffset(minutes: number): string {
  const sign = minutes < 0 ? '−' : '+';
  const abs = Math.abs(minutes);
  return `UTC${sign}${String(Math.floor(abs / 60)).padStart(2, '0')}:${String(abs % 60).padStart(2, '0')}`;
}

@Component({
  selector: 'app-delivery-settings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, PageHeaderComponent, ButtonComponent, CardComponent, CheckboxComponent, SelectFieldComponent,
    SpinnerComponent, TranslatePipe],
  templateUrl: './delivery-settings.component.html',
  styleUrl: './delivery-settings.component.scss',
})
export class DeliverySettingsComponent {
  private readonly subscriptions = inject(SubscriptionService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslationService);

  readonly loading = signal(true);
  readonly saving = signal(false);
  /** Las 24 horas como opciones del desplegable ("07:00"); el valor viaja como texto y se guarda como número. */
  readonly hourOptions: SelectOption[] = Array.from({length: 24}, (_, h) => ({
    value: String(h),
    label: `${h.toString().padStart(2, '0')}:00`,
  }));
  readonly zones = signal<string[]>(COMMON_TIME_ZONES);
  /** "Montevideo (UTC−03:00)" en lugar de "America/Montevideo", ordenadas por desfase horario. */
  readonly zoneOptions = computed<SelectOption[]>(() =>
    this.zones()
      .map((zone) => ({zone, offset: utcOffsetMinutes(zone)}))
      .sort((a, b) => a.offset - b.offset || a.zone.localeCompare(b.zone))
      .map(({zone, offset}) => ({value: zone, label: `${zoneCity(zone)} (${formatOffset(offset)})`})));

  readonly form = inject(FormBuilder).nonNullable.group({
    timezone: ['UTC', Validators.required],
    deliveryHour: ['7', Validators.required],
    // No se edita en esta pantalla: se conserva el valor guardado y se devuelve tal cual.
    maximumDailyItems: [30],
    paused: [false],
  });

  constructor() {
    this.subscriptions.settings().subscribe({
      next: (settings) => {
        const browserZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
        const zones = new Set([settings.timezone, browserZone, ...COMMON_TIME_ZONES].filter(Boolean));
        this.zones.set([...zones]);
        this.form.setValue({...settings, deliveryHour: String(settings.deliveryHour)});
        this.loading.set(false);
      },
      error: (e: AppError) => {
        this.loading.set(false);
        this.toast.error(e.message);
      },
    });
  }

  save(): void {
    if (this.form.invalid) {
      return;
    }
    this.saving.set(true);
    const value = this.form.getRawValue();
    this.subscriptions
      .updateSettings({...value, deliveryHour: Number(value.deliveryHour), maximumDailyItems: Number(value.maximumDailyItems)})
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.form.markAsPristine();
          this.toast.success(this.i18n.translate('settings.delivery.saved'));
        },
        error: (e: AppError) => {
          this.saving.set(false);
          this.toast.error(e.message);
        },
      });
  }
}
