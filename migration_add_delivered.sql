-- Migration pour ajouter le champ 'delivered' à la table message
-- Ce champ permet de savoir si un message offline a été livré au destinataire

ALTER TABLE `message`
ADD COLUMN `delivered` TINYINT(1) NOT NULL DEFAULT 0 AFTER `content`;

-- Index pour optimiser les requêtes de messages non livrés
CREATE INDEX idx_receiver_delivered ON `message` (`receiver_id`, `delivered`);

-- Index pour optimiser le nettoyage des vieux messages
CREATE INDEX idx_created_delivered ON `message` (`created_at`, `delivered`);

