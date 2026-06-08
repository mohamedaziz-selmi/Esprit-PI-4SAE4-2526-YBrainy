"""
Configuration module - Loads environment variables safely.

This module handles all configuration from the .env file.
It's the single place where we access credentials.
"""

from __future__ import annotations

import os

from dotenv import load_dotenv

# Load variables from .env file
load_dotenv()


def _parse_admin_ids(raw_value: str | None) -> frozenset[int]:
    """Convert comma-separated Telegram user IDs into integers."""
    admin_ids = set()

    for chunk in (raw_value or "").split(","):
        value = chunk.strip()
        if not value:
            continue
        try:
            admin_ids.add(int(value))
        except ValueError as exc:
            raise ValueError(
                "TELEGRAM_ADMIN_IDS must contain only numeric Telegram user IDs."
            ) from exc

    return frozenset(admin_ids)


class Settings:
    """Stores all configuration from environment variables."""

    # Telegram Bot Token - Get from BotFather on Telegram
    TELEGRAM_BOT_TOKEN = os.getenv("TELEGRAM_BOT_TOKEN")

    # GROQ API Key - Get from console.groq.com
    GROQ_API_KEY = os.getenv("GROQ_API_KEY")

    # GROQ Model to use
    GROQ_MODEL = os.getenv("GROQ_MODEL", "llama-3.3-70b-versatile")

    # Telegram admins allowed to use the bot
    TELEGRAM_ADMIN_IDS = _parse_admin_ids(os.getenv("TELEGRAM_ADMIN_IDS"))

    # MySQL Database (XAMPP)
    DB_HOST = os.getenv("DB_HOST", "127.0.0.1")
    DB_PORT = int(os.getenv("DB_PORT", "3306"))
    DB_USER = os.getenv("DB_USER", "root")
    DB_PASSWORD = os.getenv("DB_PASSWORD", "")
    DB_NAME = os.getenv("DB_NAME", "elearning_platform")

    @staticmethod
    def is_admin(telegram_user_id: int | None) -> bool:
        """Return True when the provided Telegram user ID is authorized."""
        return (
            telegram_user_id is not None
            and telegram_user_id in Settings.TELEGRAM_ADMIN_IDS
        )

    @staticmethod
    def validate():
        """Check if all required credentials are set."""
        if not Settings.TELEGRAM_BOT_TOKEN:
            raise ValueError("TELEGRAM_BOT_TOKEN not found in .env")
        if not Settings.GROQ_API_KEY:
            raise ValueError("GROQ_API_KEY not found in .env")

        print("All credentials loaded successfully.")
        if Settings.TELEGRAM_ADMIN_IDS:
            print(
                f"Admin mode enabled for {len(Settings.TELEGRAM_ADMIN_IDS)} Telegram user(s)."
            )
        else:
            print(
                "Warning: TELEGRAM_ADMIN_IDS is empty. The bot will deny admin access "
                "until you add at least one Telegram user ID. Use /myid in Telegram "
                "to discover your ID."
            )
