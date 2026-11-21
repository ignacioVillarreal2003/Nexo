-- Nexo no usa foto de perfil: el avatar son las iniciales sobre un color fijo.
ALTER TABLE user_preferences DROP COLUMN IF EXISTS photo_url;
