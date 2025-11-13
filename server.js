import http from 'http';
import express from 'express';
import cors from 'cors';
import { Server as SocketIOServer } from 'socket.io';
import dotenv from 'dotenv';
import { Sequelize, DataTypes } from 'sequelize';
import jwt from 'jsonwebtoken';
import bcrypt from 'bcryptjs';

// Load environment variables
dotenv.config();

// Basic Express setup
const app = express();
app.use(cors());
app.use(express.json());

// Create HTTP server and bind Socket.IO
const httpServer = http.createServer(app);
const io = new SocketIOServer(httpServer, {
  cors: {
    origin: '*',
    methods: ['GET', 'POST']
  }
});

// Environment variables
const PORT = process.env.PORT ? Number(process.env.PORT) : 3000;
const DB_HOST = process.env.DB_HOST || 'localhost';
const DB_PORT = process.env.DB_PORT ? Number(process.env.DB_PORT) : 3306;
const DB_USER = process.env.DB_USER || 'root';
const DB_PASSWORD = process.env.DB_PASSWORD || '';
const DB_NAME = process.env.DB_NAME || '';
const JWT_SECRET = process.env.JWT_SECRET || 'your-secret-key-change-in-production';
const JWT_EXPIRES_IN = process.env.JWT_EXPIRES_IN || '7d';

// Initialize Sequelize (MySQL)
const sequelize = new Sequelize(DB_NAME, DB_USER, DB_PASSWORD, {
  host: DB_HOST,
  port: DB_PORT,
  dialect: 'mysql',
  logging: false,
  dialectOptions: {
    // You can add timezone or SSL options here if needed
  }
});

// Define User model aligned with existing MySQL schema
const User = sequelize.define(
  'User',
  {
    id: {
      type: DataTypes.INTEGER,
      primaryKey: true,
      autoIncrement: true,
    },
    email: {
      type: DataTypes.STRING(180),
      allowNull: false,
      unique: true,
    },
    roles: {
      type: DataTypes.TEXT('long'),
      allowNull: false,
      defaultValue: '["ROLE_USER"]',
      get() {
        const rawValue = this.getDataValue('roles');
        return rawValue ? JSON.parse(rawValue) : ['ROLE_USER'];
      },
      set(value) {
        this.setDataValue('roles', JSON.stringify(value));
      }
    },
    password: {
      type: DataTypes.STRING(255),
      allowNull: false,
    },
    publicKey: {
      type: DataTypes.TEXT,
      allowNull: true,
      field: 'public_key',
    },
  },
  {
    tableName: 'user',
    timestamps: false, // Pas de gestion automatique des timestamps
  }
);

// Define Message model aligned with existing MySQL schema
const Message = sequelize.define(
  'Message',
  {
    id: {
      type: DataTypes.INTEGER,
      primaryKey: true,
      autoIncrement: true,
    },
    senderId: {
      type: DataTypes.INTEGER,
      allowNull: false,
      field: 'sender_id',
    },
    receiverId: {
      type: DataTypes.INTEGER,
      allowNull: false,
      field: 'receiver_id',
    },
    content: {
      type: DataTypes.TEXT,
      allowNull: false,
    },
    createdAt: {
      type: DataTypes.DATE,
      allowNull: false,
      field: 'created_at',
      defaultValue: DataTypes.NOW,
    },
  },
  {
    tableName: 'message',
    timestamps: false,
  }
);

// Define FriendRequest model (maps to existing 'friends' table)
const FriendRequest = sequelize.define(
  'FriendRequest',
  {
    id: {
      type: DataTypes.INTEGER,
      primaryKey: true,
      autoIncrement: true,
    },
    senderId: {
      type: DataTypes.INTEGER,
      allowNull: false,
      field: 'sender_id',
    },
    receiverId: {
      type: DataTypes.INTEGER,
      allowNull: false,
      field: 'receiver_id',
    },
    status: {
      type: DataTypes.ENUM('pending', 'accepted', 'declined'),
      defaultValue: 'pending',
    },
    createdAt: {
      type: DataTypes.DATE,
      allowNull: false,
      field: 'created_at',
      defaultValue: DataTypes.NOW,
    },
    updatedAt: {
      type: DataTypes.DATE,
      allowNull: false,
      field: 'updated_at',
      defaultValue: DataTypes.NOW,
    },
  },
  {
    tableName: 'friends',
    timestamps: false,
  }
);

// Define associations
Message.belongsTo(User, { foreignKey: 'senderId', as: 'sender' });
Message.belongsTo(User, { foreignKey: 'receiverId', as: 'receiver' });

// Friend request associations
FriendRequest.belongsTo(User, { foreignKey: 'senderId', as: 'sender' });
FriendRequest.belongsTo(User, { foreignKey: 'receiverId', as: 'receiver' });

// ============================================================================
// AUTHENTICATION MIDDLEWARE
// ============================================================================

const authenticateJWT = (req, res, next) => {
  const authHeader = req.headers.authorization;

  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'unauthorized', message: 'Token manquant ou invalide' });
  }

  const token = authHeader.substring(7); // Remove 'Bearer ' prefix

  try {
    const decoded = jwt.verify(token, JWT_SECRET);
    req.user = decoded; // { userId, email, roles }
    next();
  } catch (err) {
    console.error('JWT verification failed:', err.message);
    return res.status(401).json({ error: 'unauthorized', message: 'Token invalide ou expiré' });
  }
};

// ============================================================================
// PUBLIC ENDPOINTS
// ============================================================================

// Health check
app.get('/', (_req, res) => {
  res.json({ status: 'ok', message: 'Realtime Messaging API' });
});

// Register new user
app.post('/register', async (req, res) => {
  try {
    console.log('📝 POST /register - Début de l\'inscription');
    const { email, password, publicKey } = req.body || {};
    console.log('📝 Email:', email, '- PublicKey fournie:', publicKey ? `Oui (${publicKey.length} car)` : 'Non');

    // Validation
    if (!email || !password) {
      return res.status(400).json({ error: 'bad_request', message: 'Email et mot de passe requis' });
    }

    if (password.length < 6) {
      return res.status(400).json({ error: 'bad_request', message: 'Le mot de passe doit contenir au moins 6 caractères' });
    }

    // Check if user already exists
    const existingUser = await User.findOne({ where: { email } });
    if (existingUser) {
      return res.status(409).json({ error: 'conflict', message: 'Cet email est déjà utilisé' });
    }

    // Hash password (compatible with Symfony bcrypt)
    const hashedPassword = await bcrypt.hash(password, 13);

    // Create user
    const user = await User.create({
      email,
      password: hashedPassword,
      roles: ['ROLE_USER'],
      publicKey: publicKey || null,
    });
    console.log('✅ Utilisateur créé - ID:', user.id, '- PublicKey:', user.publicKey ? user.publicKey.substring(0, 50) + '...' : 'null');

    // Generate JWT
    const token = jwt.sign(
      {
        userId: user.id,
        email: user.email,
        roles: user.roles,
      },
      JWT_SECRET,
      { expiresIn: JWT_EXPIRES_IN }
    );

    res.status(201).json({
      message: 'Utilisateur créé avec succès',
      token,
      user: {
        id: user.id,
        email: user.email,
        roles: user.roles,
        publicKey: user.publicKey,
      },
    });
  } catch (err) {
    console.error('POST /register failed:', err);
    res.status(500).json({ error: 'internal_error', message: 'Erreur lors de la création du compte' });
  }
});

// Login
app.post('/login', async (req, res) => {
  try {
    console.log('🔐 POST /login - Début de la connexion');
    const { email, password } = req.body || {};
    console.log('🔐 Email:', email);

    // Validation
    if (!email || !password) {
      return res.status(400).json({ error: 'bad_request', message: 'Email et mot de passe requis' });
    }

    // Find user
    const user = await User.findOne({ where: { email } });
    if (!user) {
      return res.status(401).json({ error: 'unauthorized', message: 'Email ou mot de passe incorrect' });
    }

    // Verify password (compatible with Symfony bcrypt $2y$ format)
    const isPasswordValid = await bcrypt.compare(password, user.password);
    if (!isPasswordValid) {
      return res.status(401).json({ error: 'unauthorized', message: 'Email ou mot de passe incorrect' });
    }

    console.log('✅ Connexion réussie - ID:', user.id, '- PublicKey:', user.publicKey ? user.publicKey.substring(0, 50) + '...' : 'null');

    // Generate JWT
    const token = jwt.sign(
      {
        userId: user.id,
        email: user.email,
        roles: user.roles,
      },
      JWT_SECRET,
      { expiresIn: JWT_EXPIRES_IN }
    );

    res.json({
      message: 'Connexion réussie',
      token,
      user: {
        id: user.id,
        email: user.email,
        roles: user.roles,
        publicKey: user.publicKey,
      },
    });
  } catch (err) {
    console.error('POST /login failed:', err);
    res.status(500).json({ error: 'internal_error', message: 'Erreur lors de la connexion' });
  }
});

// ============================================================================
// PROTECTED ENDPOINTS (require JWT authentication)
// ============================================================================

// Get current user info
app.get('/me', authenticateJWT, async (req, res) => {
  try {
    const user = await User.findByPk(req.user.userId, {
      attributes: ['id', 'email', 'roles', 'publicKey'],
    });

    if (!user) {
      return res.status(404).json({ error: 'not_found', message: 'Utilisateur introuvable' });
    }

    res.json({
      id: user.id,
      email: user.email,
      roles: user.roles,
      publicKey: user.publicKey,
    });
  } catch (err) {
    console.error('GET /me failed:', err);
    res.status(500).json({ error: 'internal_error' });
  }
});

// Get conversation history between two users
// GET /messages?userId=2 (returns conversation between current user and user ID 2)
app.get('/messages', authenticateJWT, async (req, res) => {
  try {
    const currentUserId = req.user.userId;
    const otherUserId = req.query.userId ? Number(req.query.userId) : null;
    const limit = Math.min(Number(req.query.limit) || 50, 200);

    if (!otherUserId) {
      return res.status(400).json({ error: 'bad_request', message: 'userId requis en query parameter' });
    }

    // Get messages between current user and other user (both directions)
    const messages = await Message.findAll({
      where: {
        [Sequelize.Op.or]: [
          { senderId: currentUserId, receiverId: otherUserId },
          { senderId: otherUserId, receiverId: currentUserId },
        ],
      },
      order: [['createdAt', 'ASC']],
      limit,
      include: [
        { model: User, as: 'sender', attributes: ['id', 'email'] },
        { model: User, as: 'receiver', attributes: ['id', 'email'] },
      ],
    });

    res.json(messages);
  } catch (err) {
    console.error('GET /messages failed:', err);
    res.status(500).json({ error: 'internal_error' });
  }
});

// Send a message (REST endpoint - alternative to Socket.IO)
app.post('/messages', authenticateJWT, async (req, res) => {
  try {
    const senderId = req.user.userId;
    const { receiverId, content } = req.body || {};

    // Validation
    if (!receiverId || !content) {
      return res.status(400).json({ error: 'bad_request', message: 'receiverId et content requis' });
    }

    if (typeof content !== 'string' || content.trim().length === 0) {
      return res.status(400).json({ error: 'bad_request', message: 'Le contenu ne peut pas être vide' });
    }

    // Check if receiver exists
    const receiver = await User.findByPk(receiverId);
    if (!receiver) {
      return res.status(404).json({ error: 'not_found', message: 'Destinataire introuvable' });
    }

    // Save message to database only (no real-time emission)
    const message = await Message.create({
      senderId,
      receiverId,
      content: content.trim(),
    });

    res.status(201).json({
      id: message.id,
      senderId: message.senderId,
      receiverId: message.receiverId,
      content: message.content,
      createdAt: message.createdAt,
    });
  } catch (err) {
    console.error('POST /messages failed:', err);
    res.status(500).json({ error: 'internal_error' });
  }
});

// ============================================================================
// USER SEARCH ENDPOINT
// ============================================================================

// Search users by email (for finding friends)
app.get('/users/search', authenticateJWT, async (req, res) => {
  try {
    console.log('🔍 ===== GET /users/search =====');
    const currentUserId = req.user.userId;
    const { q, email, limit } = req.query;
    console.log('🔍 Paramètres:', { q, email, limit, currentUserId });

    // Validation
    const searchQuery = q || email;
    if (!searchQuery || typeof searchQuery !== 'string' || searchQuery.trim().length === 0) {
      console.log('❌ Paramètre de recherche manquant');
      return res.status(400).json({
        error: 'bad_request',
        message: 'Paramètre "q" ou "email" requis pour la recherche'
      });
    }

    console.log('🔍 Recherche de:', searchQuery);
    const searchLimit = limit ? Math.min(parseInt(limit), 50) : 20;

    // Search users by email (partial match)
    const users = await User.findAll({
      where: {
        email: {
          [Sequelize.Op.like]: `%${searchQuery}%`
        },
        id: {
          [Sequelize.Op.ne]: currentUserId  // Exclude current user
        }
      },
      attributes: ['id', 'email', 'roles', 'publicKey'],
      limit: searchLimit
    });

    console.log('✅ Utilisateurs trouvés:', users.length);
    users.forEach(u => {
      console.log(`   - ID: ${u.id}, Email: ${u.email}`);
    });

    res.json({
      users: users.map(u => ({
        id: u.id,
        email: u.email,
        roles: u.roles || [],
        publicKey: u.publicKey
      }))
    });
  } catch (err) {
    console.error('GET /users/search failed:', err);
    res.status(500).json({ error: 'internal_error' });
  }
});

// Get user by ID (for profile view)
app.get('/users/:id', authenticateJWT, async (req, res) => {
  try {
    const userId = parseInt(req.params.id);

    if (isNaN(userId)) {
      return res.status(400).json({ error: 'bad_request', message: 'ID utilisateur invalide' });
    }

    const user = await User.findByPk(userId, {
      attributes: ['id', 'email', 'roles', 'publicKey']
    });

    if (!user) {
      return res.status(404).json({ error: 'not_found', message: 'Utilisateur introuvable' });
    }

    res.json({
      id: user.id,
      email: user.email,
      roles: user.roles,
      publicKey: user.publicKey
    });
  } catch (err) {
    console.error('GET /users/:id failed:', err);
    res.status(500).json({ error: 'internal_error' });
  }
});

// Update user's public key
app.put('/users/public-key', authenticateJWT, async (req, res) => {
  try {
    console.log('🔑 PUT /users/public-key - Début de la mise à jour');
    console.log('🔑 User ID:', req.user?.userId);
    console.log('🔑 Body reçu:', req.body);

    const { publicKey } = req.body || {};

    if (!publicKey) {
      console.log('❌ Clé publique manquante dans la requête');
      return res.status(400).json({ error: 'bad_request', message: 'Clé publique requise' });
    }

    console.log('🔑 Clé publique reçue (longueur:', publicKey.length, '):', publicKey.substring(0, 50) + '...');

    const user = await User.findByPk(req.user.userId);
    if (!user) {
      console.log('❌ Utilisateur introuvable:', req.user.userId);
      return res.status(404).json({ error: 'not_found', message: 'Utilisateur introuvable' });
    }

    console.log('🔑 Ancien publicKey:', user.publicKey ? user.publicKey.substring(0, 50) + '...' : 'null');
    user.publicKey = publicKey;
    await user.save();
    console.log('✅ Nouveau publicKey sauvegardé:', user.publicKey.substring(0, 50) + '...');

    res.json({
      message: 'Clé publique mise à jour avec succès',
      publicKey: user.publicKey
    });
  } catch (err) {
    console.error('❌ PUT /users/public-key failed:', err);
    res.status(500).json({ error: 'internal_error', message: 'Erreur lors de la mise à jour de la clé publique' });
  }
});

// Get public key of a user
app.get('/users/:id/public-key', authenticateJWT, async (req, res) => {
  try {
    const userId = parseInt(req.params.id);

    if (isNaN(userId)) {
      return res.status(400).json({ error: 'bad_request', message: 'ID utilisateur invalide' });
    }

    const user = await User.findByPk(userId, {
      attributes: ['id', 'email', 'publicKey']
    });

    if (!user) {
      return res.status(404).json({ error: 'not_found', message: 'Utilisateur introuvable' });
    }

    if (!user.publicKey) {
      return res.status(404).json({ error: 'not_found', message: 'Clé publique non disponible' });
    }

    res.json({
      userId: user.id,
      email: user.email,
      publicKey: user.publicKey
    });
  } catch (err) {
    console.error('GET /users/:id/public-key failed:', err);
    res.status(500).json({ error: 'internal_error' });
  }
});

// ============================================================================
// FRIENDS SYSTEM ENDPOINTS
// ============================================================================

// Send a friend request
app.post('/friends/request', authenticateJWT, async (req, res) => {
  try {
    console.log('👥 ===== POST /friends/request =====');
    console.log('👥 req.user:', req.user);
    console.log('👥 req.body:', req.body);

    const senderId = req.user.userId;
    const { receiverId, receiverEmail } = req.body || {};

    console.log('👥 senderId:', senderId);
    console.log('👥 receiverId:', receiverId, '(type:', typeof receiverId, ')');
    console.log('👥 receiverEmail:', receiverEmail);

    // Validation - accept either receiverId or receiverEmail
    if (!receiverId && !receiverEmail) {
      console.log('❌ Validation failed: ni receiverId ni receiverEmail fourni');
      return res.status(400).json({
        error: 'bad_request',
        message: 'receiverId ou receiverEmail requis'
      });
    }

    // Find receiver by ID or email
    let receiver;
    if (receiverId) {
      if (typeof receiverId !== 'number') {
        return res.status(400).json({ error: 'bad_request', message: 'receiverId doit être un nombre' });
      }
      receiver = await User.findByPk(receiverId);
    } else {
      // Find by email
      if (typeof receiverEmail !== 'string' || receiverEmail.trim().length === 0) {
        return res.status(400).json({ error: 'bad_request', message: 'receiverEmail invalide' });
      }
      receiver = await User.findOne({ where: { email: receiverEmail.trim() } });
    }

    // Check if receiver exists
    if (!receiver) {
      return res.status(404).json({ error: 'not_found', message: 'Utilisateur introuvable' });
    }

    const actualReceiverId = receiver.id;

    // Cannot send request to yourself
    if (senderId === actualReceiverId) {
      return res.status(400).json({ error: 'bad_request', message: 'Vous ne pouvez pas vous ajouter vous-même' });
    }

    // Check if friend request already exists (in either direction)
    const existingRequest = await FriendRequest.findOne({
      where: {
        [Sequelize.Op.or]: [
          { senderId, receiverId: actualReceiverId },
          { senderId: actualReceiverId, receiverId: senderId }
        ]
      }
    });

    if (existingRequest) {
      if (existingRequest.status === 'accepted') {
        return res.status(409).json({ error: 'conflict', message: 'Vous êtes déjà amis' });
      }
      if (existingRequest.status === 'pending') {
        return res.status(409).json({ error: 'conflict', message: 'Demande déjà envoyée' });
      }
    }

    // Create friend request
    const friendRequest = await FriendRequest.create({
      senderId,
      receiverId: actualReceiverId,
      status: 'pending'
    });

    // Get sender info for notification
    const sender = await User.findByPk(senderId, {
      attributes: ['id', 'email']
    });

    // Emit real-time notification to receiver via Socket.IO
    io.to(`user_${actualReceiverId}`).emit('friend_request', {
      id: friendRequest.id,
      sender: {
        id: sender.id,
        email: sender.email
      },
      status: friendRequest.status,
      createdAt: friendRequest.createdAt
    });

    res.status(201).json({
      message: 'Demande d\'ami envoyée',
      request: {
        id: friendRequest.id,
        senderId: friendRequest.senderId,
        receiverId: friendRequest.receiverId,
        status: friendRequest.status,
        createdAt: friendRequest.createdAt
      }
    });
  } catch (err) {
    console.error('POST /friends/request failed:', err);
    res.status(500).json({ error: 'internal_error', message: 'Erreur lors de l\'envoi de la demande' });
  }
});

// Get pending friend requests (received)
app.get('/friends/requests', authenticateJWT, async (req, res) => {
  try {
    const userId = req.user.userId;

    const requests = await FriendRequest.findAll({
      where: {
        receiverId: userId,
        status: 'pending'
      },
      include: [
        {
          model: User,
          as: 'sender',
          attributes: ['id', 'email']
        }
      ],
      order: [['createdAt', 'DESC']]
    });

    res.json({
      requests: requests.map(req => ({
        id: req.id,
        sender: {
          id: req.sender.id,
          email: req.sender.email
        },
        status: req.status,
        createdAt: req.createdAt
      }))
    });
  } catch (err) {
    console.error('GET /friends/requests failed:', err);
    res.status(500).json({ error: 'internal_error' });
  }
});

// Accept or reject a friend request
app.put('/friends/request/:id', authenticateJWT, async (req, res) => {
  try {
    const userId = req.user.userId;
    const requestId = parseInt(req.params.id);
    const { action } = req.body || {}; // 'accept' or 'decline'

    // Validation
    if (!action || !['accept', 'decline'].includes(action)) {
      return res.status(400).json({ error: 'bad_request', message: 'action doit être "accept" ou "decline"' });
    }

    // Find the friend request
    const friendRequest = await FriendRequest.findByPk(requestId);

    if (!friendRequest) {
      return res.status(404).json({ error: 'not_found', message: 'Demande introuvable' });
    }

    // Check if current user is the receiver
    if (friendRequest.receiverId !== userId) {
      return res.status(403).json({ error: 'forbidden', message: 'Vous ne pouvez pas modifier cette demande' });
    }

    // Check if already processed
    if (friendRequest.status !== 'pending') {
      return res.status(400).json({ error: 'bad_request', message: 'Cette demande a déjà été traitée' });
    }

    // Update status
    const newStatus = action === 'accept' ? 'accepted' : 'declined';
    await friendRequest.update({ status: newStatus });

    // Get receiver info for notification
    const receiver = await User.findByPk(userId, {
      attributes: ['id', 'email']
    });

    // Emit real-time notification to sender (person who sent the request)
    io.to(`user_${friendRequest.senderId}`).emit('friend_request_response', {
      requestId: friendRequest.id,
      status: newStatus,
      responder: {
        id: receiver.id,
        email: receiver.email
      },
      updatedAt: new Date()
    });

    res.json({
      message: action === 'accept' ? 'Demande acceptée' : 'Demande refusée',
      request: {
        id: friendRequest.id,
        status: newStatus
      }
    });
  } catch (err) {
    console.error('PUT /friends/request/:id failed:', err);
    res.status(500).json({ error: 'internal_error' });
  }
});

// Get list of friends (accepted requests)
app.get('/friends', authenticateJWT, async (req, res) => {
  try {
    const userId = req.user.userId;

    // Get all accepted friend requests where user is either sender or receiver
    const friendRequests = await FriendRequest.findAll({
      where: {
        [Sequelize.Op.or]: [
          { senderId: userId },
          { receiverId: userId }
        ],
        status: 'accepted'
      },
      include: [
        {
          model: User,
          as: 'sender',
          attributes: ['id', 'email']
        },
        {
          model: User,
          as: 'receiver',
          attributes: ['id', 'email']
        }
      ],
      order: [['updatedAt', 'DESC']]
    });

    // Map to get the friend (the other user)
    const friends = friendRequests.map(req => {
      const friend = req.senderId === userId ? req.receiver : req.sender;
      return {
        friendshipId: req.id,
        friend: {
          id: friend.id,
          email: friend.email
        },
        since: req.updatedAt
      };
    });

    res.json({ friends });
  } catch (err) {
    console.error('GET /friends failed:', err);
    res.status(500).json({ error: 'internal_error' });
  }
});

// Remove a friend (delete friendship)
app.delete('/friends/:id', authenticateJWT, async (req, res) => {
  try {
    const userId = req.user.userId;
    const friendshipId = parseInt(req.params.id);

    // Find the friend request
    const friendRequest = await FriendRequest.findByPk(friendshipId);

    if (!friendRequest) {
      return res.status(404).json({ error: 'not_found', message: 'Amitié introuvable' });
    }

    // Check if user is part of this friendship
    if (friendRequest.requesterId !== userId && friendRequest.receiverId !== userId) {
      return res.status(403).json({ error: 'forbidden', message: 'Vous ne pouvez pas supprimer cette amitié' });
    }

    // Delete the friendship
    await friendRequest.destroy();

    res.json({ message: 'Ami supprimé avec succès' });
  } catch (err) {
    console.error('DELETE /friends/:id failed:', err);
    res.status(500).json({ error: 'internal_error' });
  }
});

// ============================================================================
// POLLING ENDPOINTS FOR MESSAGES
// ============================================================================

// Get new messages since a specific timestamp (for polling)
app.get('/messages/new', authenticateJWT, async (req, res) => {
  try {
    const userId = req.user.userId;
    const since = req.query.since ? new Date(req.query.since) : null;
    const otherUserId = req.query.userId ? Number(req.query.userId) : null;

    if (!since) {
      return res.status(400).json({
        error: 'bad_request',
        message: 'Paramètre "since" requis (ISO 8601 timestamp)'
      });
    }

    // Build query for new messages
    const whereClause = {
      createdAt: { [Sequelize.Op.gt]: since }
    };

    // If userId specified, only get messages from/to that user
    if (otherUserId) {
      whereClause[Sequelize.Op.or] = [
        { senderId: userId, receiverId: otherUserId },
        { senderId: otherUserId, receiverId: userId }
      ];
    } else {
      // Get all messages involving the user
      whereClause[Sequelize.Op.or] = [
        { senderId: userId },
        { receiverId: userId }
      ];
    }

    const newMessages = await Message.findAll({
      where: whereClause,
      order: [['createdAt', 'ASC']],
      limit: 100,
      include: [
        { model: User, as: 'sender', attributes: ['id', 'email'] },
        { model: User, as: 'receiver', attributes: ['id', 'email'] }
      ]
    });

    res.json({
      messages: newMessages,
      timestamp: new Date().toISOString()
    });
  } catch (err) {
    console.error('GET /messages/new failed:', err);
    res.status(500).json({ error: 'internal_error' });
  }
});

// Get unread message count per conversation
app.get('/messages/unread-count', authenticateJWT, async (req, res) => {
  try {
    const userId = req.user.userId;
    const since = req.query.since ? new Date(req.query.since) : null;

    const whereClause = {
      receiverId: userId
    };

    if (since) {
      whereClause.createdAt = { [Sequelize.Op.gt]: since };
    }

    // Get all messages received by user and group by sender
    const messages = await Message.findAll({
      where: whereClause,
      attributes: [
        'senderId',
        [sequelize.fn('COUNT', sequelize.col('id')), 'count'],
        [sequelize.fn('MAX', sequelize.col('created_at')), 'lastMessageAt']
      ],
      include: [
        { model: User, as: 'sender', attributes: ['id', 'email'] }
      ],
      group: ['senderId', 'sender.id', 'sender.email']
    });

    res.json({
      unreadCounts: messages.map(m => ({
        senderId: m.senderId,
        senderEmail: m.sender.email,
        unreadCount: parseInt(m.getDataValue('count')),
        lastMessageAt: m.getDataValue('lastMessageAt')
      })),
      timestamp: new Date().toISOString()
    });
  } catch (err) {
    console.error('GET /messages/unread-count failed:', err);
    res.status(500).json({ error: 'internal_error' });
  }
});

// ============================================================================
// SOCKET.IO - FRIEND REQUESTS REAL-TIME NOTIFICATIONS ONLY
// ============================================================================

// Store connected users: { userId: socketId }
const connectedUsers = new Map();

io.on('connection', (socket) => {
  console.log('Socket connected:', socket.id);

  // Authenticate socket connection
  socket.on('authenticate', async (data) => {
    try {
      const { token } = data || {};

      if (!token) {
        socket.emit('error', { message: 'Token manquant' });
        return;
      }

      // Verify JWT
      const decoded = jwt.verify(token, JWT_SECRET);
      const userId = decoded.userId;

      // Store user - map userId to socketId
      connectedUsers.set(userId, socket.id);
      socket.userId = userId;

      // Join user's personal room for notifications
      socket.join(`user_${userId}`);

      console.log(`User ${userId} authenticated on socket ${socket.id}`);
      socket.emit('authenticated', { userId, message: 'Authentification réussie' });
    } catch (err) {
      console.error('Socket authentication failed:', err.message);
      socket.emit('error', { message: 'Token invalide' });
    }
  });

  // Disconnect
  socket.on('disconnect', () => {
    if (socket.userId) {
      console.log(`User ${socket.userId} disconnected (socket ${socket.id})`);
      connectedUsers.delete(socket.userId);
    } else {
      console.log('Socket disconnected:', socket.id);
    }
  });
});

// ============================================================================
// START SERVER
// ============================================================================

async function start() {
  try {
    await sequelize.authenticate();
    // Do not alter existing schema; ensure models are usable
    await sequelize.sync({ alter: false });
    console.log('Database connected and models synced.');

    httpServer.listen(PORT, () => {
      console.log(`✅ Server listening on port ${PORT}`);
      console.log(`📡 Socket.IO ready for friend request notifications`);
      console.log(`💬 Messages via REST API (polling recommended)`);
      console.log(`🔐 JWT authentication enabled`);
    });
  } catch (err) {
    console.error('Faiiled to start server:', err);
    process.exit(1);
  }
}

start();