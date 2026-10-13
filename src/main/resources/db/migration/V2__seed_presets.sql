-- Regras prontas, copiadas de legacy-web/src/lib/presets.ts.
-- Para mudar uma regra, crie uma nova migração (V3, V4...). Nunca edite esta depois de aplicada.

INSERT INTO rule_presets (id, sort_order, name, short_name, description) VALUES
    ('50-30-20', 1, 'Regra 50/30/20', '50/30/20',
     'A clássica: metade para o essencial, 30% para o estilo de vida e 20% para o futuro.'),
    ('70-20-10', 2, 'Regra 70/20/10', '70/20/10',
     '70% para o custo de vida, 20% para investir e 10% para sonhos, doações ou quitar dívidas.'),
    ('6-potes', 3, 'Método dos 6 potes', '6 potes',
     'Divide a renda em seis potes com objetivos claros, do essencial à liberdade financeira.'),
    ('80-20', 4, 'Pague-se primeiro', '80/20',
     'O mais simples: separe 20% para o futuro assim que receber e viva com os outros 80%.');

INSERT INTO rule_preset_buckets (preset_id, sort_order, name, percent, kind, description, claimed_categories) VALUES
    ('50-30-20', 0, 'Necessidades', 50, 'NEEDS', 'Moradia, contas, mercado, transporte e saúde', ''),
    ('50-30-20', 1, 'Desejos', 30, 'WANTS', 'Lazer, restaurantes, compras e assinaturas', ''),
    ('50-30-20', 2, 'Futuro', 20, 'FUTURE', 'Reserva de emergência, investimentos e metas', ''),

    ('70-20-10', 0, 'Custo de vida', 70, 'NEEDS', 'Todos os gastos do dia a dia', ''),
    ('70-20-10', 1, 'Futuro', 20, 'FUTURE', 'Reserva de emergência e investimentos', ''),
    ('70-20-10', 2, 'Sonhos e doações', 10, 'OTHER', 'Objetivos, doações ou quitar dívidas', 'goals,gifts'),

    ('6-potes', 0, 'Necessidades', 55, 'NEEDS', 'Custos essenciais do mês', ''),
    ('6-potes', 1, 'Liberdade financeira', 10, 'FUTURE', 'Investimentos para gerar renda passiva', 'investments'),
    ('6-potes', 2, 'Longo prazo', 10, 'FUTURE', 'Reserva de emergência e grandes compras', 'emergency_reserve,goals'),
    ('6-potes', 3, 'Educação', 10, 'OTHER', 'Cursos, livros e desenvolvimento', 'education'),
    ('6-potes', 4, 'Diversão', 10, 'WANTS', 'Lazer, restaurantes e mimos sem culpa', ''),
    ('6-potes', 5, 'Doações', 5, 'OTHER', 'Ajudar pessoas e causas', 'gifts'),

    ('80-20', 0, 'Gastos do mês', 80, 'NEEDS', 'Tudo o que você consome no mês', ''),
    ('80-20', 1, 'Futuro', 20, 'FUTURE', 'Reserva de emergência e investimentos', '');
