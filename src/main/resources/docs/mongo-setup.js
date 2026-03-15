db = db.getSiblingDB('expense_tracker_dev');

db.createCollection('users');
db.createCollection('transactions');
db.createCollection('goals');
db.createCollection('investments');
db.createCollection('budgets');
db.createCollection('ai_insights');
db.createCollection('raw_ingestion_log');

// Users
db.users.createIndex({ "email": 1 }, { unique: true });

// Transactions
db.transactions.createIndex({ "userId": 1, "date": -1 });
db.transactions.createIndex({ "userId": 1, "category": 1 });
db.transactions.createIndex({ "userId": 1, "source": 1 });
db.transactions.createIndex({ "userId": 1, "status": 1 });
db.transactions.createIndex({ "userId": 1, "upiPlatform": 1 });

// Raw Ingestion Log
db.raw_ingestion_log.createIndex({ "userId": 1, "createdAt": -1 });
db.raw_ingestion_log.createIndex({ "userId": 1, "parsingStatus": 1 });
db.raw_ingestion_log.createIndex({ "rawContent": "hashed" });  // For deduplication

// Goals
db.goals.createIndex({ "userId": 1, "status": 1 });

// Investments
db.investments.createIndex({ "userId": 1, "type": 1 });

// Budgets
db.budgets.createIndex({ "userId": 1, "month": 1 }, { unique: true });

// AI Insights
db.ai_insights.createIndex({ "userId": 1, "generatedAt": -1 });
db.ai_insights.createIndex({ "expiresAt": 1 }, { expireAfterSeconds: 0 });

print("Database 'expense_tracker_dev' initialized with collections and indexes.");
