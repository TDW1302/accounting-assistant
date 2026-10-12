-- Portee de date par defaut du fournisseur (DKV mensuel, Vanbrada annuel, ...).
-- Sert a prefiller la portee lors de la creation d'une facture.
ALTER TABLE supplier
    ADD COLUMN default_date_scope VARCHAR(255);
