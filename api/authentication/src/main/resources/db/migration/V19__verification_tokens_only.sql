-- Los tokens solo verifican el email: el cambio de email desde la cuenta ya no existe.
DELETE FROM email_verification_tokens WHERE purpose <> 'EMAIL_VERIFICATION';
ALTER TABLE email_verification_tokens DROP CONSTRAINT chk_email_verification_tokens_purpose;
ALTER TABLE email_verification_tokens DROP COLUMN purpose;
ALTER TABLE email_verification_tokens DROP COLUMN new_email;
