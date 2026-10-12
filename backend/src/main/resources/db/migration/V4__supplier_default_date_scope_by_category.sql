-- Portee par defaut deduite de la categorie, pour les fiches qui n'en ont pas.
-- Les fiches deja reglees a la main ne sont pas touchees: elles portent une
-- decision plus fine que la categorie (DKV est une assurance, mais mensuelle).

UPDATE supplier
SET default_date_scope = 'MONTHLY'
WHERE default_date_scope IS NULL
  AND category IN ('ABONNEMENT', 'LOYER', 'HONORAIRES', 'TELECOM');

UPDATE supplier
SET default_date_scope = 'YEARLY'
WHERE default_date_scope IS NULL
  AND category = 'ASSURANCE';

-- Le reste, categorie absente comprise.
UPDATE supplier
SET default_date_scope = 'DAILY'
WHERE default_date_scope IS NULL;
