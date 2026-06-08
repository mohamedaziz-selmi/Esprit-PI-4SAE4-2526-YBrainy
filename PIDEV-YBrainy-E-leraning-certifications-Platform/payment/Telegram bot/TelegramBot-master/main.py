"""
Main Entry Point - Starts the chatbot.

This is where everything comes together!
"""

from config.settings import Settings
from bot.telegram_bot import TelegramBot


def main():
    """Start the entire application."""
    print("🚀 Starting Telegram Chatbot with GROQ LLM...")
    print()

    # Step 1: Validate credentials are set
    Settings.validate()

    # Step 2: Create and start the bot
    bot = TelegramBot()
    bot.start()


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\n👋 Application stopped by user.")
    except Exception as e:
        print(f"\n❌ Application error: {e}")
        import traceback
        traceback.print_exc()
