/** Nombre de un país (código ISO 3166-1 alfa-2) en el idioma de la app; si el navegador no sabe, el código. */
export function countryName(code: string | null | undefined, locale: string): string {
  if (!code) {
    return '';
  }
  try {
    return new Intl.DisplayNames([locale], {type: 'region'}).of(code.toUpperCase()) ?? code;
  } catch {
    return code;
  }
}
