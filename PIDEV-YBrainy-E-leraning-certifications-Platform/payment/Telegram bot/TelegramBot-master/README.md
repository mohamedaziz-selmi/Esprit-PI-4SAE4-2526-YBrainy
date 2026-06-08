# 🤖 Telegram Chatbot with GROQ LLM

A simple, student-friendly AI chatbot that runs on Telegram using the free GROQ API.

## 📚 What This Project Does

This chatbot:
- Receives messages on Telegram
- Sends them to the GROQ LLM (free & fast)
- Returns intelligent responses back to the user
- Is built in a clean, modular way so you can reuse it later

## 🏗️ Project Structure Explained

```
project/
├── bot/
│   └── telegram_bot.py          # Telegram communication logic
│
├── agent/
│   └── agent.py                 # The "brain" that processes messages
│
├── llm/
│   └── groq_client.py           # Talks to GROQ API
│
├── config/
│   └── settings.py              # Loads credentials from .env
│
├── main.py                      # Starts the bot
├── requirements.txt             # Python packages needed
├── .env                         # Your secret credentials (don't commit!)
└── README.md                    # This file
```

### Folder Breakdown

| Folder | Purpose |
|--------|---------|
| `bot/` | Everything related to Telegram (sending/receiving messages) |
| `agent/` | The intelligent "brain" (processes messages, calls LLM) |
| `llm/` | Talks to GROQ API (gets intelligent responses) |
| `config/` | Safely loads environment variables |

## 🔄 How It Works (Simple Flow)

```
User sends message on Telegram
    ↓
Telegram Bot receives it
    ↓
Agent processes the message
    ↓
Agent asks GROQ LLM for answer
    ↓
GROQ returns intelligent response
    ↓
Bot sends response back to user
```

## 🚀 Quick Start

### Step 1: Clone or Download This Project

```bash
cd c:\Users\moham\Desktop\WORK\ChatbotTelgram
```

### Step 2: Install Python Packages

```bash
pip install -r requirements.txt
```

### Step 3: Get Your Credentials

#### 🔐 Telegram Bot Token
1. Open Telegram and search for `@BotFather`
2. Send `/start`
3. Send `/newbot`
4. Choose a name and username
5. Copy the token (looks like: `123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11`)

#### 🔐 GROQ API Key
1. Go to https://console.groq.com
2. Sign up (free)
3. Go to "API Keys"
4. Create a new key
5. Copy it

### Step 4: Add Credentials to .env

Open the `.env` file and replace:

```env
TELEGRAM_BOT_TOKEN=your_actual_bot_token_here
GROQ_API_KEY=your_actual_groq_key_here
```

### Step 5: Run the Bot

```bash
python main.py
```

You should see:
```
🚀 Starting Telegram Chatbot with GROQ LLM...

✅ All credentials loaded successfully!
🤖 Bot is starting...
Press Ctrl+C to stop the bot
```

### Step 6: Test It!

Open Telegram and find your bot (use the username you created).
- Send `/start` to see the welcome message
- Send any message and the bot will respond!

## 📖 Understanding Each Module

### `config/settings.py`
**What it does:** Loads secrets from `.env` safely
**Why it matters:** Keeps credentials out of code

```python
# This is how we access credentials anywhere in the project:
Settings.TELEGRAM_BOT_TOKEN
Settings.GROQ_API_KEY
```

### `llm/groq_client.py`
**What it does:** Talks to GROQ API, sends text, gets responses
**Why it matters:** Separates LLM logic from the rest of the code

```python
client = GroqClient()
response = client.get_response("Hello!")
# Returns: "Hello! How can I help you today?"
```

### `agent/agent.py`
**What it does:** The "brain" that orchestrates everything
**Why it matters:** Processes user input before sending to LLM

```python
agent = ChatAgent()
response = agent.process_message("Tell me a joke")
# Returns: "Why did the programmer quit? Because they didn't get arrays!"
```

### `bot/telegram_bot.py`
**What it does:** Receives messages from Telegram, sends responses
**Why it matters:** The connection between user and agent

- `handle_start()` → Responds to `/start` command
- `handle_message()` → Processes regular messages
- Shows "typing..." indicator (good UX!)

### `main.py`
**What it does:** Starts everything!
**Why it matters:** Single entry point to run the bot

## 🔐 Security - Why We Do This

| What | Why |
|------|-----|
| `.env` file | Stores secrets outside of code |
| `.gitignore` | Prevents `.env` from being committed to git |
| `Settings` class | Single place to manage credentials |
| No hardcoded tokens | Makes code safe to share |

## 🎮 Testing Commands

Once your bot is running, try these on Telegram:

```
/start                  → Welcome message
Hello!                  → Bot responds
Write a poem            → Bot writes a poem
Tell me a joke          → Bot tells a joke
Explain quantum physics → Bot explains it
```

## 🛠️ How to Customize

### Change the LLM Model
Edit `.env`:
```env
GROQ_MODEL=llama-2-70b-chat
```

Available free models:
- `mixtral-8x7b-32768` (fastest, default)
- `llama-2-70b-chat` (most capable)
- `gemma-7b-it` (lightweight)

### Change Welcome Message
Edit `bot/telegram_bot.py` → `handle_start()` method

### Add Commands
Edit `bot/telegram_bot.py`:
```python
self.app.add_handler(CommandHandler("help", self.handle_help))
```

## 🐛 Troubleshooting

### "TELEGRAM_BOT_TOKEN not found"
- Check your `.env` file is in the project root
- Make sure you didn't leave it as `your_telegram_bot_token_here`

### "GROQ_API_KEY not found"
- Visit https://console.groq.com and create an API key
- Add it to `.env`

### Bot doesn't respond
- Make sure `.env` has valid tokens
- Check internet connection
- Press Ctrl+C and restart: `python main.py`

### "ModuleNotFoundError"
- Run: `pip install -r requirements.txt`

## 📚 What You Learned

This project teaches:
- **Separation of Concerns** → Different modules do different jobs
- **Security** → How to handle secrets safely
- **API Integration** → Talking to external services (Telegram, GROQ)
- **Clean Code** → Making code that's easy to understand and extend
- **Error Handling** → Gracefully handling problems

## 🚀 Next Steps (Improvements)

You can enhance this bot later:
1. **Memory** → Remember conversation history
2. **Databases** → Store user conversations
3. **Multiple Agents** → Different behaviors for different tasks
4. **Web Dashboard** → Monitor the bot online
5. **Docker** → Easy deployment
6. **Logging** → Track what happened

## 📝 License

This is a learning project. Feel free to modify and share!

---

**Happy Coding! 🎉**

Questions? Read through the code - every file has helpful comments explaining what's happening.
