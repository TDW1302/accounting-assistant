-- Toute facture doit porter son auteur, sauf celles reprises de l'Excel: elles
-- preexistent a l'application et n'y ont ete encodees par personne. Jusqu'ici
-- rien ne le garantissait — createdBy etait simplement laisse nul quand aucune
-- session n'etait ouverte, notamment lors du scan d'inbox planifie de 3h.

-- Auteur des factures que l'application cree d'elle-meme. Compte technique: il
-- n'est pas connectable (enabled a false, mot de passe volontairement inexploitable
-- par BCrypt) et n'existe que pour etre designe comme auteur.
INSERT INTO app_user (username, email, password, role, enabled,
                      password_changed_at, password_expires_at, created_at)
VALUES ('system', 'system@localhost', 'x-not-a-bcrypt-hash-login-impossible', 'USER', FALSE,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

ALTER TABLE invoice ADD COLUMN source VARCHAR(255);

-- Reprise de l'existant. L'origine exacte des lignes sans auteur n'est plus
-- reconstituable — import Excel et scan de nuit laissaient tous deux createdBy
-- nul — d'ou LEGACY plutot qu'une attribution inventee.
UPDATE invoice SET source = CASE
    WHEN created_by_id IS NULL          THEN 'LEGACY'
    WHEN falco_document_id IS NOT NULL  THEN 'PEPPOL'
    ELSE 'MANUAL'
END;

ALTER TABLE invoice ALTER COLUMN source SET NOT NULL;

-- La regle elle-meme, hors de portee du code applicatif: aucun chemin de
-- creation, present ou futur, ne peut produire une facture sans auteur en
-- dehors des deux cas prevus.
ALTER TABLE invoice ADD CONSTRAINT ck_invoice_author_required
    CHECK (created_by_id IS NOT NULL OR source IN ('EXCEL_IMPORT', 'LEGACY'));
