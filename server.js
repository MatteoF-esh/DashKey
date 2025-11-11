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
  dialectOptions: {}
});

// Define User model
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
  },
  {
    tableName: 'user',
    timestamps: false,
  }
);

// Definee Message model - ONLY for offline/pending messages
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
    delivered: {
      type: DataTypes.BOOLEAN,
      allowNull: false,
      defaultValue: false,
      field: 'delivered'
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

// Define FriendRequest model
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
FriendRequest.belongsTo(User, { foreignKey: 'senderId', as: 'sender' });
FriendRequest.belongsTo(User, { foreignKey: 'receiverId', as: 'receiver' });

// ============================================================================
// ONLINE USERS TRACKING
// ============================================================================

// Map userId to Set of socketIds (a user can have multiple devices)
const userSockets = new Map();

function isUserOnline(userId) {
  const sockets = userSockets.get(userId);
  return sockets && sockets.size > 0;
}

function getUserSockets(userId) {
  return userSockets.get(userId) || new Set();
}

function addUserSocket(userId, socketId) {
  if (!userSockets.has(userId)) {
    userSockets.set(userId, new Set());
  }
  userSockets.get(userId).add(socketId);
  console.log(`✅ User ${userId} now has ${userSockets.get(userId).size} socket(s) connected`);
}

function removeUserSocket(userId, socketId) {
  const sockets = userSockets.get(userId);
  if (sockets) {
    sockets.delete(socketId);
    if (sockets.size === 0) {
      userSockets.delete(userId);
      console.log(`❌ User ${userId} is now offline`);
    } else {
      console.log(`⚠️ User ${userId} still has ${sockets.size} socket(s) connected`);
    }
  }
}

// ============================================================================
// AUTHENTICATION MIDDLEWARE
// ============================================================================

const authenticateJWT = (req, res, next) => {
  const authHeader = req.headers.authorization;

  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'unauthorized', message: 'Token manquant ou invalide' });
  }

  const token = authHeader.substring(7);

  try {
    const decoded = jwt.verify(token, JWT_SECRET);
    req.user = decoded;
    next();
  } catch (err) {
    console.error('JWT verification failed:', err.message);
    return res.status(401).json({ error: 'unauthorized', message: 'Token invalide ou expiré' });
  }
};

// ============================================================================
// PUBLIC ENDPOINTS
// ============================================================================

app.get('/', (_req, res) => {
  res.json({ status: 'ok', message: 'Realtime Messaging API with Direct Delivery' });
});

// Register
app.post('/register', async (req, res) => {
  try {
    const { email, password } = req.body || {};

    if (!email || !password) {
      return res.status(400).json({ error: 'bad_request', message: 'Email et mot de passe requis' });
    }

    if (password.length < 6) {
      return res.status(400).json({ error: 'bad_request', message: 'Le mot de passe doit contenir au moins 6 caractères' });
    }

    const existingUser = await User.findOne({ where: { email } });
    if (existingUser) {
      return res.status(409).json({ error: 'conflict', message: 'Cet email est déjà utilisé' });
    }

    const hashedPassword = await bcrypt.hash(password, 13);

    const user = await User.create({
      email,
      password: hashedPassword,
      roles: ['ROLE_USER'],
    });

    const token = jwt.sign(
      { userId: user.id, email: user.email, roles: user.roles },
      JWT_SECRET,
      { expiresIn: JWT_EXPIRES_IN }
    );

    res.status(201).json({
      message: 'Utilisateur créé avec succès',
      token,
      user: { id: user.id, email: user.email, roles: user.roles },
    });
  } catch (err) {
    console.error('POST /register failed:', err);
    res.status(500).json({ error: 'internal_error', message: 'Erreur lors de la création du compte' });
  }
});

// Login
app.post('/login', async (req, res) => {
  try {
    const { email, password } = req.body || {};

    if (!email || !password) {
      return res.status(400).json({ error: 'bad_request', message: 'Email et mot de passe requis' });
    }

    const user = await User.findOne({ where: { email } });
    if (!user) {
      return res.status(401).json({ error: 'unauthorized', message: 'Email ou mot de passe incorrect' });
    }

    const isPasswordValid = await bcrypt.compare(password, user.password);
    if (!isPasswordValid) {
      return res.status(401).json({ error: 'unauthorized', message: 'Email ou mot de passe incorrect' });
    }

    const token = jwt.sign(
      { userId: user.id, email: user.email, roles: user.roles },
      JWT_SECRET,
      { expiresIn: JWT_EXPIRES_IN }
    );

    res.json({
      message: 'Connexion réussie',
      token,
      user: { id: user.id, email: user.email, roles: user.roles },
    });
  } catch (err) {
    console.error('POST /login failed:', err);
    res.status(500).json({ error: 'internal_error', message: 'Erreur lors de la connexion' });
  }
});

// ============================================================================
// PROTECTED ENDPOINTS
// ============================================================================

app.get('/me', authenticateJWT, async (req, res) => {
  try {
    const user = await User.findByPk(req.user.userId, {
      attributes: ['id', 'email', 'roles'],
    });

    if (!user) {
      return res.status(404).json({ error: 'not_found', message: 'Utilisateur introuvable' });
    }

    res.json({ id: user.id, email: user.email, roles: user.roles });
  } catch (err) {
    console.error('GET /me failed:', err);
    res.status(500).json({ error: 'internal_error' });
  }
});

// Get conversation history - COMPATIBILITÉ AVEC ANCIEN CODE ANDROID
// Note: Dans le nouveau système, les messages sont stockés localement sur le téléphone
// Cet endpoint retourne une liste vide car il n'y a plus d'historique côté serveur
app.get('/messages', authenticateJWT, async (req, res) => {
  try {
    const currentUserId = req.user.userId;
    const otherUserId = req.query.userId ? Number(req.query.userId) : null;

    if (!otherUserId) {
      return res.status(400).json({ error: 'bad_request', message: 'userId requis en query parameter' });
    }

    console.log(`[GET /messages] User ${currentUserId} demande l'historique avec user ${otherUserId}`);
    console.log(`⚠️ Note: Dans le nouveau système, les messages sont stockés localement sur l'appareil`);

    // Retourner une liste vide car les messages sont maintenant stockés localement
    // L'application doit utiliser sa base de données Room locale
    res.json([]);
  } catch (err) {
    console.error('GET /messages failed:', err);
    res.status(500).json({ error: 'internal_error' });
  }
});

// Get offline/pending messages (not delivered yet)
app.get('/messages/pending', authenticateJWT, async (req, res) => {
  try {
    const userId = req.user.userId;

    const messages = await Message.findAll({
      where: {
        receiverId: userId,
        delivered: false
      },
      order: [['createdAt', 'ASC']],
      include: [
        { model: User, as: 'sender', attributes: ['id', 'email'] }
      ],
    });

    res.json({ messages });
  } catch (err) {
    console.error('GET /messages/pending failed:', err);
    res.status(500).json({ error: 'internal_error' });
  }
});

// Send a message via REST - COMPATIBILITÉ AVEC ANCIEN CODE ANDROID
// Note: Préférez utiliser Socket.IO pour la livraison directe en temps réel
app.post('/messages', authenticateJWT, async (req, res) => {
  try {
    const senderId = req.user.userId;
    const { receiverId, content } = req.body || {};

    if (!receiverId || !content) {
      return res.status(400).json({ error: 'bad_request', message: 'receiverId et content requis' });
    }

    if (typeof content !== 'string' || content.trim().length === 0) {
      return res.status(400).json({ error: 'bad_request', message: 'Le contenu ne peut pas être vide' });
    }

    const receiver = await User.findByPk(receiverId);
    if (!receiver) {
      return res.status(404).json({ error: 'not_found', message: 'Destinataire introuvable' });
    }

    console.log(`[POST /messages] Envoi message de ${senderId} vers ${receiverId} (via REST)`);

    // Vérifier si le destinataire est en ligne
    const receiverSockets = getUserSockets(receiverId);

    if (receiverSockets.size > 0) {
      // LIVRAISON DIRECTE - Le destinataire est en ligne
      console.log(`📨 Livraison directe via Socket.IO (destinataire en ligne)`);

      const sender = await User.findByPk(senderId, { attributes: ['email'] });
      const messageData = {
        senderId,
        senderEmail: sender.email,
        receiverId,
        content: content.trim(),
        timestamp: Date.now()
      };

      receiverSockets.forEach(socketId => {
        io.to(socketId).emit('message', messageData);
      });

      // Retourner une réponse factice pour l'API (le message n'est PAS stocké en BDD)
      res.status(201).json({
        id: 0, // ID fictif
        senderId,
        receiverId,
        content: content.trim(),
        createdAt: new Date().toISOString(),
        delivered: true
      });
    } else {
      // STOCKAGE OFFLINE - Le destinataire est hors ligne
      console.log(`💾 Destinataire offline, stockage en BDD`);

      const message = await Message.create({
        senderId,
        receiverId,
        content: content.trim(),
        delivered: false
      });

      res.status(201).json({
        id: message.id,
        senderId: message.senderId,
        receiverId: message.receiverId,
        content: message.content,
        createdAt: message.createdAt,
        delivered: false
      });
    }
  } catch (err) {
    console.error('POST /messages failed:', err);
    res.status(500).json({ error: 'internal_error' });
  }
});

// Mark messages as delivered (client confirms receipt)
app.post('/messages/delivered', authenticateJWT, async (req, res) => {
  try {
    const { messageIds } = req.body || {};

    if (!Array.isArray(messageIds)) {
      return res.status(400).json({ error: 'bad_request', message: 'messageIds array required' });
    }

    await Message.update(
      { delivered: true },
      { where: { id: messageIds } }
    );

    res.json({ message: 'Messages marked as delivered', count: messageIds.length });
  } catch (err) {
    console.error('POST /messages/delivered failed:', err);
    res.status(500).json({ error: 'internal_error' });
  }
});

// Search user by email
app.get('/users/search', authenticateJWT, async (req, res) => {
  try {
    const currentUserId = req.user.userId;
    const { email } = req.query;

    if (!email || typeof email !== 'string' || email.trim().length === 0) {
      return res.status(400).json({
        error: 'bad_request',
        message: 'Paramètre "email" requis'
      });
    }

    const user = await User.findOne({
      where: {
        email: email.trim(),
        id: { [Sequelize.Op.ne]: currentUserId }
      },
      attributes: ['id', 'email', 'roles']
    });

    if (!user) {
      return res.status(404).json({ error: 'not_found', message: 'Utilisateur introuvable' });
    }

    res.json({ id: user.id, email: user.email, roles: user.roles });
  } catch (err) {
    console.error('GET /users/search failed:', err);
    res.status(500).json({ error: 'internal_error' });
  }
});

// ============================================================================
// FRIENDS SYSTEM
// ============================================================================

app.post('/friends/request', authenticateJWT, async (req, res) => {
  try {
    const senderId = req.user.userId;
    const { receiverId } = req.body || {};

    if (!receiverId) {
      return res.status(400).json({ error: 'bad_request', message: 'receiverId requis' });
    }

    const actualReceiverId = typeof receiverId === 'number' ? receiverId : parseInt(receiverId);

    if (senderId === actualReceiverId) {
      return res.status(400).json({ error: 'bad_request', message: 'Vous ne pouvez pas vous ajouter vous-même' });
    }

    const receiver = await User.findByPk(actualReceiverId);
    if (!receiver) {
      return res.status(404).json({ error: 'not_found', message: 'Utilisateur introuvable' });
    }

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

    const friendRequest = await FriendRequest.create({
      senderId,
      receiverId: actualReceiverId,
      status: 'pending'
    });

    // Notify receiver if online
    const receiverSockets = getUserSockets(actualReceiverId);
    receiverSockets.forEach(socketId => {
      io.to(socketId).emit('friend_request_received', {
        id: friendRequest.id,
        senderId,
        senderEmail: req.user.email,
        createdAt: friendRequest.createdAt
      });
    });

    res.status(201).json({
      message: 'Demande d\'ami envoyée',
      id: friendRequest.id,
      senderId: friendRequest.senderId,
      receiverId: friendRequest.receiverId,
      status: friendRequest.status,
      createdAt: friendRequest.createdAt
    });
  } catch (err) {
    console.error('POST /friends/request failed:', err);
    res.status(500).json({ error: 'internal_error', message: 'Erreur lors de l\'envoi de la demande' });
  }
});

app.get('/friends/requests', authenticateJWT, async (req, res) => {
  try {
    const userId = req.user.userId;

    const requests = await FriendRequest.findAll({
      where: { receiverId: userId, status: 'pending' },
      include: [{ model: User, as: 'sender', attributes: ['id', 'email'] }],
      order: [['createdAt', 'DESC']]
    });

    res.json({
      requests: requests.map(req => ({
        id: req.id,
        sender: { id: req.sender.id, email: req.sender.email },
        status: req.status,
        createdAt: req.createdAt
      }))
    });
  } catch (err) {
    console.error('GET /friends/requests failed:', err);
    res.status(500).json({ error: 'internal_error' });
  }
});

app.put('/friends/request/:id', authenticateJWT, async (req, res) => {
  try {
    const userId = req.user.userId;
    const requestId = parseInt(req.params.id);
    const { action } = req.body || {};

    if (!action || !['accept', 'decline'].includes(action)) {
      return res.status(400).json({ error: 'bad_request', message: 'action doit être "accept" ou "decline"' });
    }

    const friendRequest = await FriendRequest.findByPk(requestId);

    if (!friendRequest) {
      return res.status(404).json({ error: 'not_found', message: 'Demande introuvable' });
    }

    if (friendRequest.receiverId !== userId) {
      return res.status(403).json({ error: 'forbidden', message: 'Vous ne pouvez pas modifier cette demande' });
    }

    if (friendRequest.status !== 'pending') {
      return res.status(400).json({ error: 'bad_request', message: 'Cette demande a déjà été traitée' });
    }

    const newStatus = action === 'accept' ? 'accepted' : 'declined';
    await friendRequest.update({ status: newStatus, updatedAt: new Date() });

    // Notify sender if online
    const senderSockets = getUserSockets(friendRequest.senderId);
    senderSockets.forEach(socketId => {
      io.to(socketId).emit('friend_request_updated', {
        requestId: friendRequest.id,
        status: newStatus,
        userId: userId
      });
    });

    res.json({
      message: action === 'accept' ? 'Demande acceptée' : 'Demande refusée',
      id: friendRequest.id,
      status: newStatus
    });
  } catch (err) {
    console.error('PUT /friends/request/:id failed:', err);
    res.status(500).json({ error: 'internal_error' });
  }
});

app.get('/friends', authenticateJWT, async (req, res) => {
  try {
    const userId = req.user.userId;

    const friendRequests = await FriendRequest.findAll({
      where: {
        [Sequelize.Op.or]: [{ senderId: userId }, { receiverId: userId }],
        status: 'accepted'
      },
      include: [
        { model: User, as: 'sender', attributes: ['id', 'email'] },
        { model: User, as: 'receiver', attributes: ['id', 'email'] }
      ],
      order: [['updatedAt', 'DESC']]
    });

    const friends = friendRequests.map(req => {
      const friend = req.senderId === userId ? req.receiver : req.sender;
      return {
        friendshipId: req.id,
        friend: { id: friend.id, email: friend.email },
        since: req.updatedAt
      };
    });

    res.json({ friends });
  } catch (err) {
    console.error('GET /friends failed:', err);
    res.status(500).json({ error: 'internal_error' });
  }
});

app.delete('/friends/:id', authenticateJWT, async (req, res) => {
  try {
    const userId = req.user.userId;
    const friendshipId = parseInt(req.params.id);

    const friendRequest = await FriendRequest.findByPk(friendshipId);

    if (!friendRequest) {
      return res.status(404).json({ error: 'not_found', message: 'Amitié introuvable' });
    }

    if (friendRequest.senderId !== userId && friendRequest.receiverId !== userId) {
      return res.status(403).json({ error: 'forbidden', message: 'Vous ne pouvez pas supprimer cette amitié' });
    }

    const otherUserId = friendRequest.senderId === userId ? friendRequest.receiverId : friendRequest.senderId;

    await friendRequest.destroy();

    // Notify other user if online
    const otherUserSockets = getUserSockets(otherUserId);
    otherUserSockets.forEach(socketId => {
      io.to(socketId).emit('friendship_deleted', {
        friendshipId,
        deletedBy: userId
      });
    });

    res.json({ message: 'Ami supprimé avec succès' });
  } catch (err) {
    console.error('DELETE /friends/:id failed:', err);
    res.status(500).json({ error: 'internal_error' });
  }
});

// ============================================================================
// SOCKET.IO - DIRECT MESSAGE DELIVERY
// ============================================================================

io.on('connection', (socket) => {
  console.log('🔌 Socket connected:', socket.id);

  socket.on('authenticate', async (data) => {
    try {
      const { token } = data || {};

      if (!token) {
        socket.emit('error', { message: 'Token manquant' });
        return;
      }

      const decoded = jwt.verify(token, JWT_SECRET);
      const userId = decoded.userId;

      socket.userId = userId;
      addUserSocket(userId, socket.id);

      console.log(`✅ User ${userId} authenticated`);
      socket.emit('authenticated', { userId, message: 'Authentifié' });

      // Deliver pending offline messages
      const pendingMessages = await Message.findAll({
        where: { receiverId: userId, delivered: false },
        order: [['createdAt', 'ASC']],
        include: [{ model: User, as: 'sender', attributes: ['id', 'email'] }]
      });

      if (pendingMessages.length > 0) {
        console.log(`📬 Delivering ${pendingMessages.length} pending message(s) to user ${userId}`);

        pendingMessages.forEach(msg => {
          socket.emit('message', {
            id: msg.id,
            senderId: msg.senderId,
            senderEmail: msg.sender.email,
            content: msg.content,
            timestamp: msg.createdAt.getTime(),
            fromServer: true
          });
        });

        // Mark as delivered
        const messageIds = pendingMessages.map(m => m.id);
        await Message.update({ delivered: true }, { where: { id: messageIds } });
      }
    } catch (err) {
      console.error('❌ Socket authentication failed:', err.message);
      socket.emit('error', { message: 'Token invalide' });
    }
  });

  // MAIN MESSAGE HANDLER - DIRECT DELIVERY
  socket.on('send_message', async (data) => {
    try {
      const { receiverId, content } = data || {};

      if (!socket.userId) {
        socket.emit('error', { message: 'Non authentifié' });
        return;
      }

      if (!receiverId || !content || typeof content !== 'string' || content.trim().length === 0) {
        socket.emit('error', { message: 'receiverId et content requis' });
        return;
      }

      const receiver = await User.findByPk(receiverId);
      if (!receiver) {
        socket.emit('error', { message: 'Destinataire introuvable' });
        return;
      }

      const sender = await User.findByPk(socket.userId, { attributes: ['email'] });

      const messageData = {
        senderId: socket.userId,
        senderEmail: sender.email,
        receiverId,
        content: content.trim(),
        timestamp: Date.now()
      };

      // Check if receiver is online
      const receiverSockets = getUserSockets(receiverId);

      if (receiverSockets.size > 0) {
        // DIRECT DELIVERY - receiver is online
        console.log(`📨 Direct delivery from ${socket.userId} to ${receiverId} (${receiverSockets.size} device(s))`);

        let deliveredCount = 0;
        receiverSockets.forEach(socketId => {
          io.to(socketId).emit('message', messageData);
          deliveredCount++;
        });

        // Confirm to sender
        socket.emit('message_delivered', {
          tempId: data.tempId, // if client sends a temp ID
          receiverId,
          timestamp: messageData.timestamp,
          direct: true
        });

        console.log(`✅ Message delivered directly to ${deliveredCount} device(s)`);
      } else {
        // STORE FOR OFFLINE DELIVERY
        console.log(`💾 Receiver ${receiverId} offline, storing message in DB`);

        const savedMessage = await Message.create({
          senderId: socket.userId,
          receiverId,
          content: content.trim(),
          delivered: false
        });

        // Confirm to sender (stored for later)
        socket.emit('message_stored', {
          tempId: data.tempId,
          messageId: savedMessage.id,
          receiverId,
          timestamp: savedMessage.createdAt.getTime(),
          offline: true
        });

        console.log(`💾 Message stored with ID ${savedMessage.id}`);
      }

    } catch (err) {
      console.error('❌ Error handling send_message:', err);
      socket.emit('error', { message: 'Erreur lors de l\'envoi du message' });
    }
  });

  socket.on('disconnect', () => {
    if (socket.userId) {
      removeUserSocket(socket.userId, socket.id);
      console.log(`👋 User ${socket.userId} socket ${socket.id} disconnected`);
    } else {
      console.log('👋 Anonymous socket disconnected:', socket.id);
    }
  });
});

// ============================================================================
// CLEANUP JOB - Delete old delivered messages
// ============================================================================

async function cleanupOldMessages() {
  try {
    const oneDayAgo = new Date(Date.now() - 24 * 60 * 60 * 1000);

    const deleted = await Message.destroy({
      where: {
        delivered: true,
        createdAt: { [Sequelize.Op.lt]: oneDayAgo }
      }
    });

    if (deleted > 0) {
      console.log(`🗑️ Cleaned up ${deleted} old delivered message(s)`);
    }
  } catch (err) {
    console.error('❌ Cleanup job failed:', err);
  }
}

// Run cleanup every hour
setInterval(cleanupOldMessages, 60 * 60 * 1000);

// ============================================================================
// START SERVER
// ============================================================================

async function start() {
  try {
    await sequelize.authenticate();
    await sequelize.sync({ alter: false });
    console.log('✅ Database connected');

    httpServer.listen(PORT, () => {
      console.log(`\n🚀 Server listening on port ${PORT}`);
      console.log(`📡 Socket.IO ready for DIRECT message delivery`);
      console.log(`💾 Messages stored in DB ONLY when receiver is offline`);
      console.log(`🔐 JWT authentication enabled\n`);
    });

    // Run initial cleanup
    await cleanupOldMessages();
  } catch (err) {
    console.error('❌ Failed to start server:', err);
    process.exit(1);
  }
}

start();

