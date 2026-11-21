-- V6 creó country_code como CHAR(2); la entidad lo mapea como VARCHAR(2).
ALTER TABLE topics ALTER COLUMN country_code TYPE VARCHAR(2);
