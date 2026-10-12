/**
 * Miroir de PasswordPolicy cote backend. Les deux ecrans annoncaient encore
 * « min. 6 caracteres » quand l'API en exigeait huit avec minuscule, majuscule,
 * chiffre et caractere special: la saisie partait, l'API la refusait.
 */
export const PASSWORD_MIN_LENGTH = 8;
export const PASSWORD_MAX_LENGTH = 128;
export const PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^a-zA-Z0-9]).+$/;

export const PASSWORD_HINT =
  'Min. 8 caractères, avec minuscule, majuscule, chiffre et caractère spécial';

export function isPasswordValid(value: string): boolean {
  return (
    value.length >= PASSWORD_MIN_LENGTH &&
    value.length <= PASSWORD_MAX_LENGTH &&
    PASSWORD_PATTERN.test(value)
  );
}
