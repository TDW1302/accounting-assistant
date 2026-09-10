-- Rattacher l'historique a un modele recurrent.
--
-- Les loyers repris de l'Excel sont deja au facturier, numerotes dans la serie
-- documentee (042, 043...). Ce numero est la reference comptable de l'annee
-- concernee: le changer pour D001 reecrirait un enregistrement passe. Le lien
-- vers le modele cesse donc d'exiger la serie EXPENSE — seule la periode
-- couverte reste obligatoire, puisque c'est elle qui identifie l'echeance et
-- empeche qu'un mois soit compte deux fois.

ALTER TABLE invoice DROP CONSTRAINT ck_invoice_recurring_is_expense;

ALTER TABLE invoice ADD CONSTRAINT ck_invoice_recurring_period
    CHECK (recurring_expense_id IS NULL OR scope_date IS NOT NULL);
