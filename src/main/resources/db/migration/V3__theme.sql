-- Tema escolhido em Ajustes. SYSTEM segue o claro ou escuro do aparelho, como era antes desta coluna.
ALTER TABLE user_settings ADD COLUMN theme VARCHAR(10) DEFAULT 'SYSTEM' NOT NULL;
ALTER TABLE user_settings ADD CONSTRAINT user_settings_theme_check CHECK (theme IN ('SYSTEM', 'LIGHT', 'DARK'));
