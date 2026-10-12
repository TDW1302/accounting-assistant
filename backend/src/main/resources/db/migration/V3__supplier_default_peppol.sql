-- Valeur par defaut du drapeau "recu par Peppol" a la creation d'une facture.
ALTER TABLE supplier
    ADD COLUMN default_peppol BOOLEAN NOT NULL DEFAULT FALSE;

-- Reprise de l'existant: tout passe a true sauf les restaurants, dont les tickets
-- arrivent sur papier. IS DISTINCT FROM couvre aussi les fiches sans categorie.
UPDATE supplier
SET default_peppol = TRUE
WHERE category IS DISTINCT FROM 'RESTAURANT';
