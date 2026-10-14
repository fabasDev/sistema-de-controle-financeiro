-- O visual passou a ser o azul-noite (escuro) por padrão. Quem estava em SYSTEM recebeu esse valor
-- só porque era o padrão da V3: a escolha de tema chegou junto com ela e o app ainda não estava no
-- ar. Essas contas passam para o novo padrão; Claro e Automático continuam em Ajustes.
ALTER TABLE user_settings ALTER COLUMN theme SET DEFAULT 'DARK';
UPDATE user_settings SET theme = 'DARK' WHERE theme = 'SYSTEM';
