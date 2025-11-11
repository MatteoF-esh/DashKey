-- ============================================================================
-- SCRIPT SQL DE VÉRIFICATION - Système de demandes d'amis
-- ============================================================================

-- 1. VÉRIFIER LA STRUCTURE DE LA TABLE 'friends'
-- ============================================================================
DESCRIBE friends;

-- Structure attendue :
-- +-------------+----------------------------------------------+------+-----+---------------------+----------------+
-- | Field       | Type                                         | Null | Key | Default             | Extra          |
-- +-------------+----------------------------------------------+------+-----+---------------------+----------------+
-- | id          | int                                          | NO   | PRI | NULL                | auto_increment |
-- | sender_id   | int                                          | NO   | MUL | NULL                |                |
-- | receiver_id | int                                          | NO   | MUL | NULL                |                |
-- | status      | enum('pending','accepted','declined')        | YES  |     | pending             |                |
-- | created_at  | datetime                                     | NO   |     | CURRENT_TIMESTAMP   |                |
-- | updated_at  | datetime                                     | NO   |     | CURRENT_TIMESTAMP   |                |
-- +-------------+----------------------------------------------+------+-----+---------------------+----------------+


-- 2. LISTER TOUS LES UTILISATEURS
-- ============================================================================
SELECT id, email FROM user ORDER BY id;


-- 3. VÉRIFIER TOUTES LES DEMANDES D'AMIS
-- ============================================================================
SELECT
    f.id,
    f.sender_id,
    u1.email AS sender_email,
    f.receiver_id,
    u2.email AS receiver_email,
    f.status,
    f.created_at,
    f.updated_at
FROM friends f
LEFT JOIN user u1 ON f.sender_id = u1.id
LEFT JOIN user u2 ON f.receiver_id = u2.id
ORDER BY f.created_at DESC;


-- 4. VÉRIFIER LES DEMANDES EN ATTENTE (PENDING)
-- ============================================================================
SELECT
    f.id,
    u1.email AS sender_email,
    u2.email AS receiver_email,
    f.created_at
FROM friends f
JOIN user u1 ON f.sender_id = u1.id
JOIN user u2 ON f.receiver_id = u2.id
WHERE f.status = 'pending'
ORDER BY f.created_at DESC;


-- 5. VÉRIFIER LES DEMANDES POUR UN UTILISATEUR SPÉCIFIQUE
-- ============================================================================
-- Remplacer X par l'ID de votre utilisateur
SET @user_id = 1; -- ⚠️ MODIFIER ICI

-- Demandes REÇUES par cet utilisateur
SELECT
    f.id,
    u1.email AS sender_email,
    f.status,
    f.created_at
FROM friends f
JOIN user u1 ON f.sender_id = u1.id
WHERE f.receiver_id = @user_id
ORDER BY f.created_at DESC;

-- Demandes ENVOYÉES par cet utilisateur
SELECT
    f.id,
    u2.email AS receiver_email,
    f.status,
    f.created_at
FROM friends f
JOIN user u2 ON f.receiver_id = u2.id
WHERE f.sender_id = @user_id
ORDER BY f.created_at DESC;


-- 6. VÉRIFIER LES AMIS ACCEPTÉS
-- ============================================================================
SELECT
    f.id,
    u1.email AS user1_email,
    u2.email AS user2_email,
    f.updated_at AS friends_since
FROM friends f
JOIN user u1 ON f.sender_id = u1.id
JOIN user u2 ON f.receiver_id = u2.id
WHERE f.status = 'accepted'
ORDER BY f.updated_at DESC;


-- 7. STATISTIQUES
-- ============================================================================
SELECT
    'Total demandes' AS type,
    COUNT(*) AS count
FROM friends
UNION ALL
SELECT
    'Pending' AS type,
    COUNT(*) AS count
FROM friends
WHERE status = 'pending'
UNION ALL
SELECT
    'Accepted' AS type,
    COUNT(*) AS count
FROM friends
WHERE status = 'accepted'
UNION ALL
SELECT
    'Declined' AS type,
    COUNT(*) AS count
FROM friends
WHERE status = 'declined';


-- ============================================================================
-- REQUÊTES DE TEST (OPTIONNEL - À UTILISER EN DÉVELOPPEMENT)
-- ============================================================================

-- INSÉRER UNE DEMANDE D'AMI DE TEST (si nécessaire)
-- Remplacer les IDs par des IDs valides de votre table user
-- INSERT INTO friends (sender_id, receiver_id, status, created_at, updated_at)
-- VALUES (1, 2, 'pending', NOW(), NOW());


-- ACCEPTER UNE DEMANDE D'AMI MANUELLEMENT
-- Remplacer X par l'ID de la demande
-- UPDATE friends
-- SET status = 'accepted', updated_at = NOW()
-- WHERE id = X;


-- SUPPRIMER UNE DEMANDE D'AMI
-- Remplacer X par l'ID de la demande
-- DELETE FROM friends WHERE id = X;


-- NETTOYER TOUTES LES DEMANDES (ATTENTION!)
-- ⚠️ NE PAS UTILISER EN PRODUCTION
-- DELETE FROM friends;


-- ============================================================================
-- VÉRIFICATIONS DE SÉCURITÉ
-- ============================================================================

-- Vérifier qu'il n'y a pas de demandes en doublon
SELECT
    sender_id,
    receiver_id,
    COUNT(*) AS count
FROM friends
GROUP BY sender_id, receiver_id
HAVING COUNT(*) > 1;

-- Vérifier qu'il n'y a pas de demandes où sender_id = receiver_id
SELECT * FROM friends WHERE sender_id = receiver_id;


-- ============================================================================
-- CRÉER LA TABLE SI ELLE N'EXISTE PAS
-- ============================================================================
/*
CREATE TABLE IF NOT EXISTS friends (
    id INT AUTO_INCREMENT PRIMARY KEY,
    sender_id INT NOT NULL,
    receiver_id INT NOT NULL,
    status ENUM('pending', 'accepted', 'declined') DEFAULT 'pending',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (sender_id) REFERENCES user(id) ON DELETE CASCADE,
    FOREIGN KEY (receiver_id) REFERENCES user(id) ON DELETE CASCADE,

    INDEX idx_receiver_status (receiver_id, status),
    INDEX idx_sender_status (sender_id, status),

    UNIQUE KEY unique_friendship (sender_id, receiver_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
*/


-- ============================================================================
-- REQUÊTES UTILES POUR LE DÉBOGAGE
-- ============================================================================

-- Voir les dernières demandes créées
SELECT
    f.id,
    u1.email AS de,
    u2.email AS vers,
    f.status,
    f.created_at
FROM friends f
JOIN user u1 ON f.sender_id = u1.id
JOIN user u2 ON f.receiver_id = u2.id
ORDER BY f.id DESC
LIMIT 10;


-- Compter les demandes par statut pour chaque utilisateur
SELECT
    u.id,
    u.email,
    SUM(CASE WHEN f.status = 'pending' AND f.receiver_id = u.id THEN 1 ELSE 0 END) AS demandes_recues,
    SUM(CASE WHEN f.status = 'pending' AND f.sender_id = u.id THEN 1 ELSE 0 END) AS demandes_envoyees,
    SUM(CASE WHEN f.status = 'accepted' AND (f.sender_id = u.id OR f.receiver_id = u.id) THEN 1 ELSE 0 END) AS amis
FROM user u
LEFT JOIN friends f ON (f.sender_id = u.id OR f.receiver_id = u.id)
GROUP BY u.id, u.email
ORDER BY u.id;

