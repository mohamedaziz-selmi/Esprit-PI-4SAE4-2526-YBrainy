"""
Telegram Bot - Handles Telegram commands and messages.
"""

from __future__ import annotations

import logging
import re
import time
from typing import Any, Dict, List

import telebot
from requests.exceptions import ConnectionError, Timeout
from telebot import apihelper

from agent.agent import ChatAgent
from bot.pack_database import PackDatabaseService
from config.settings import Settings

# Configure API helper with better timeout settings
apihelper.SESSION_TIMEOUT = 10

# Set up logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class TelegramBot:
    """Telegram bot that only serves authorized admins."""

    PACK_LOOKUP_PATTERN = re.compile(r"^(?:show|get|view)\s+pack\s+(?P<id>\d+)$", re.IGNORECASE)
    CATEGORY_LOOKUP_PATTERN = re.compile(
        r"^(?:show|get|view)\s+(?:pack\s+)?category\s+(?P<id>\d+)$",
        re.IGNORECASE,
    )
    FIELDS_PATTERN = re.compile(
        r"^(?:show|list)\s+(?:fields|columns)\s+(?P<table>pack|packs|category|categories|pack_category|pack_categories)$",
        re.IGNORECASE,
    )
    PACK_UPDATE_PATTERN = re.compile(
        r"^update\s+pack\s+(?P<id>\d+)\s+(?P<changes>.+)$",
        re.IGNORECASE,
    )
    CATEGORY_UPDATE_PATTERN = re.compile(
        r"^update\s+(?:pack\s+)?category\s+(?P<id>\d+)\s+(?P<changes>.+)$",
        re.IGNORECASE,
    )

    def __init__(self):
        """Initialize bot services and handlers."""
        self.agent = ChatAgent()
        self.pack_db = PackDatabaseService()
        self.bot = telebot.TeleBot(Settings.TELEGRAM_BOT_TOKEN)

        @self.bot.message_handler(commands=["start"])
        def handle_start(message):
            """Handle /start command."""
            if Settings.is_admin(getattr(message.from_user, "id", None)):
                self.bot.reply_to(message, self._admin_welcome_message())
            else:
                self.bot.reply_to(message, self._access_denied_message(message))

        @self.bot.message_handler(commands=["myid"])
        def handle_myid(message):
            """Return the caller's Telegram user ID."""
            self.bot.reply_to(message, self._format_identity_message(message))

        @self.bot.message_handler(commands=["help"])
        def handle_help(message):
            """Handle /help command."""
            self._run_admin_action(
                message,
                lambda: self.bot.reply_to(message, self._admin_help_message()),
                "Could not load the admin help message.",
            )

        @self.bot.message_handler(commands=["packs"])
        def handle_packs_command(message):
            """Handle /packs command."""
            self._run_admin_action(
                message,
                lambda: self._reply_with_packs(message),
                "Could not load packs right now.",
            )

        @self.bot.message_handler(commands=["pack"])
        def handle_pack_command(message):
            """Handle /pack <id> command."""
            self._run_admin_action(
                message,
                lambda: self._reply_with_pack(message, self._extract_command_args(message.text)),
                "Could not load that pack right now.",
            )

        @self.bot.message_handler(commands=["pack_categories", "categories"])
        def handle_pack_categories_command(message):
            """Handle /pack_categories command."""
            self._run_admin_action(
                message,
                lambda: self._reply_with_pack_categories(message),
                "Could not load pack categories right now.",
            )

        @self.bot.message_handler(commands=["category"])
        def handle_category_command(message):
            """Handle /category <id> command."""
            self._run_admin_action(
                message,
                lambda: self._reply_with_category(
                    message, self._extract_command_args(message.text)
                ),
                "Could not load that pack category right now.",
            )

        @self.bot.message_handler(commands=["fields", "columns"])
        def handle_fields_command(message):
            """Handle /fields <table> command."""
            self._run_admin_action(
                message,
                lambda: self._reply_with_fields(
                    message, self._extract_command_args(message.text)
                ),
                "Could not load the table schema right now.",
            )

        @self.bot.message_handler(commands=["update_pack"])
        def handle_update_pack_command(message):
            """Handle /update_pack <id> field=value | field=value command."""
            self._run_admin_action(
                message,
                lambda: self._handle_update_pack_command(
                    message, self._extract_command_args(message.text)
                ),
                "Could not update that pack right now.",
            )

        @self.bot.message_handler(commands=["update_category", "update_pack_category"])
        def handle_update_category_command(message):
            """Handle /update_category <id> field=value | field=value command."""
            self._run_admin_action(
                message,
                lambda: self._handle_update_category_command(
                    message, self._extract_command_args(message.text)
                ),
                "Could not update that pack category right now.",
            )

        @self.bot.message_handler(
            content_types=["text"],
            func=lambda message: not ((message.text or "").strip().startswith("/")),
        )
        def handle_message(message):
            """Handle incoming admin messages."""
            if not self._ensure_admin(message):
                return

            try:
                text = (message.text or "").strip()
                if not text:
                    self.bot.reply_to(message, "Send a text message or use /help.")
                    return

                self.bot.send_chat_action(message.chat.id, "typing")

                if self._handle_text_admin_command(message, text):
                    return

                if self._is_pack_categories_question(text):
                    self._reply_with_pack_categories(message)
                    return

                if self._is_packs_question(text):
                    self._reply_with_packs(message)
                    return

                response = self.agent.process_message(text)
                self.bot.reply_to(message, response)
            except (LookupError, RuntimeError, ValueError) as exc:
                logger.warning("Admin text request failed: %s", exc)
                self.bot.reply_to(message, str(exc))
            except Exception:
                logger.exception("Error processing admin message")
                self.bot.reply_to(
                    message,
                    "Sorry, I encountered an error processing your admin request. Please try again.",
                )

    def _run_admin_action(self, message, action, fallback_message: str) -> None:
        """Authorize, run, and handle user-friendly errors for admin actions."""
        if not self._ensure_admin(message):
            return

        try:
            action()
        except (LookupError, RuntimeError, ValueError) as exc:
            logger.warning("Admin request failed: %s", exc)
            self.bot.reply_to(message, str(exc))
        except Exception:
            logger.exception("Unexpected admin action failure")
            self.bot.reply_to(message, fallback_message)

    def _ensure_admin(self, message) -> bool:
        """Reject any user who is not in TELEGRAM_ADMIN_IDS."""
        user_id = getattr(message.from_user, "id", None)
        if Settings.is_admin(user_id):
            return True

        logger.warning(
            "Unauthorized access attempt from Telegram user %s",
            user_id,
        )
        self.bot.reply_to(message, self._access_denied_message(message))
        return False

    @staticmethod
    def _extract_command_args(text: str | None) -> str:
        """Return everything that appears after the command name."""
        parts = (text or "").split(maxsplit=1)
        return parts[1].strip() if len(parts) > 1 else ""

    def _admin_welcome_message(self) -> str:
        """Message shown to authorized admins on /start."""
        return (
            "YBrainy admin bot is ready.\n\n"
            "I only respond to authorized Telegram admins.\n"
            "You can inspect and update packs or pack categories directly from Telegram.\n\n"
            "Useful commands:\n"
            "/packs\n"
            "/pack <id>\n"
            "/pack_categories\n"
            "/category <id>\n"
            "/fields packs\n"
            "/fields pack_categories\n"
            "/update_pack <id> field=value | field=value\n"
            "/update_category <id> field=value | field=value\n"
            "/help"
        )

    def _admin_help_message(self) -> str:
        """Explain the supported admin commands with examples."""
        return (
            "Admin commands:\n"
            "/packs -> list packs with IDs\n"
            "/pack 12 -> show every field for pack 12\n"
            "/pack_categories -> list categories with IDs\n"
            "/category 4 -> show every field for category 4\n"
            "/fields packs -> list available pack columns\n"
            "/fields pack_categories -> list available category columns\n"
            "/update_pack 12 title=AI Starter Pack | sale_price=49.99 | status=ACTIVE\n"
            "/update_category 4 name=Cloud | status=ACTIVE | description=Updated category text\n"
            "/myid -> show your Telegram user ID\n\n"
            "You can also send text like:\n"
            "show pack 12\n"
            "show fields packs\n"
            "update pack 12 status=ACTIVE | sale_price=59.99\n\n"
            "Use the exact column names from /fields. Separate multiple updates with |. "
            "For status and level, use enum-style values such as ACTIVE, DRAFT, ARCHIVED, BEGINNER, INTERMEDIATE, or ADVANCED."
        )

    def _access_denied_message(self, message) -> str:
        """Message shown to non-admin Telegram users."""
        user_id = getattr(message.from_user, "id", "unknown")
        return (
            "Access denied. This bot is reserved for platform admins only.\n"
            f"Your Telegram user ID is: {user_id}\n"
            "If you should have access, add this ID to TELEGRAM_ADMIN_IDS in the bot's .env file."
        )

    def _format_identity_message(self, message) -> str:
        """Show Telegram identity data for easy admin configuration."""
        user = getattr(message, "from_user", None)
        username = getattr(user, "username", None) or "not set"
        full_name = " ".join(
            part
            for part in [
                getattr(user, "first_name", "") or "",
                getattr(user, "last_name", "") or "",
            ]
            if part
        ).strip() or "not available"
        username_line = (
            f"Username: @{username}" if username != "not set" else f"Username: {username}"
        )

        return (
            f"Telegram user ID: {getattr(user, 'id', 'unknown')}\n"
            f"{username_line}\n"
            f"Full name: {full_name}\n"
            "Add your numeric user ID to TELEGRAM_ADMIN_IDS in .env."
        )

    @staticmethod
    def _normalize_text(text: str) -> str:
        return " ".join((text or "").lower().split())

    def _is_packs_question(self, text: str) -> bool:
        normalized = self._normalize_text(text)
        phrases = (
            "show packs",
            "list packs",
            "available packs",
            "what packs",
            "packs available",
            "all packs",
            "pack list",
        )
        return normalized in {"packs", "pack"} or any(
            phrase in normalized for phrase in phrases
        )

    def _is_pack_categories_question(self, text: str) -> bool:
        normalized = self._normalize_text(text)
        phrases = (
            "pack categories",
            "show categories",
            "list categories",
            "categories of packs",
            "what categories",
        )
        return normalized in {"categories", "pack category"} or any(
            phrase in normalized for phrase in phrases
        )

    def _handle_text_admin_command(self, message, text: str) -> bool:
        """Support chat-style admin commands without requiring slash commands."""
        normalized = self._normalize_text(text)
        if normalized in {"help", "commands"}:
            self.bot.reply_to(message, self._admin_help_message())
            return True

        match = self.PACK_LOOKUP_PATTERN.match(text)
        if match:
            self._reply_with_pack(message, match.group("id"))
            return True

        match = self.CATEGORY_LOOKUP_PATTERN.match(text)
        if match:
            self._reply_with_category(message, match.group("id"))
            return True

        match = self.FIELDS_PATTERN.match(text)
        if match:
            self._reply_with_fields(message, match.group("table"))
            return True

        match = self.PACK_UPDATE_PATTERN.match(text)
        if match:
            self._update_pack(
                message,
                int(match.group("id")),
                self._parse_update_pairs(match.group("changes")),
            )
            return True

        match = self.CATEGORY_UPDATE_PATTERN.match(text)
        if match:
            self._update_category(
                message,
                int(match.group("id")),
                self._parse_update_pairs(match.group("changes")),
            )
            return True

        return False

    def _reply_with_packs(self, message) -> None:
        packs = self.pack_db.fetch_packs()
        response = self._format_packs_response(packs)
        self._send_long_message(message.chat.id, response)

    def _reply_with_pack(self, message, pack_id_text: str) -> None:
        pack_id = self._parse_numeric_id(pack_id_text, "pack")
        pack = self.pack_db.fetch_pack(pack_id)
        if not pack:
            raise LookupError(f"No pack found with id {pack_id}.")
        self._send_long_message(
            message.chat.id,
            self._format_record_response("Pack", pack),
        )

    def _reply_with_pack_categories(self, message) -> None:
        categories = self.pack_db.fetch_pack_categories()
        response = self._format_pack_categories_response(categories)
        self._send_long_message(message.chat.id, response)

    def _reply_with_category(self, message, category_id_text: str) -> None:
        category_id = self._parse_numeric_id(category_id_text, "pack category")
        category = self.pack_db.fetch_pack_category(category_id)
        if not category:
            raise LookupError(f"No pack category found with id {category_id}.")
        self._send_long_message(
            message.chat.id,
            self._format_record_response("Pack Category", category),
        )

    def _reply_with_fields(self, message, table_name: str) -> None:
        if not table_name:
            raise ValueError("Usage: /fields packs or /fields pack_categories")
        normalized_table = self.pack_db._normalize_table_name(table_name)
        fields = self.pack_db.fetch_table_columns(normalized_table)
        self._send_long_message(
            message.chat.id,
            self._format_fields_response(normalized_table, fields),
        )

    def _handle_update_pack_command(self, message, command_args: str) -> None:
        pack_id, updates = self._parse_update_command_arguments(
            command_args,
            usage="Usage: /update_pack <id> field=value | field=value",
        )
        self._update_pack(message, pack_id, updates)

    def _handle_update_category_command(self, message, command_args: str) -> None:
        category_id, updates = self._parse_update_command_arguments(
            command_args,
            usage="Usage: /update_category <id> field=value | field=value",
        )
        self._update_category(message, category_id, updates)

    def _update_pack(self, message, pack_id: int, updates: Dict[str, str]) -> None:
        updated_pack = self.pack_db.update_pack(pack_id, updates)
        response = (
            f"Pack {pack_id} updated successfully.\n\n"
            f"{self._format_record_response('Pack', updated_pack)}"
        )
        self._send_long_message(message.chat.id, response)

    def _update_category(
        self, message, category_id: int, updates: Dict[str, str]
    ) -> None:
        updated_category = self.pack_db.update_pack_category(category_id, updates)
        response = (
            f"Pack category {category_id} updated successfully.\n\n"
            f"{self._format_record_response('Pack Category', updated_category)}"
        )
        self._send_long_message(message.chat.id, response)

    @staticmethod
    def _parse_numeric_id(value: str, label: str) -> int:
        """Validate positive integer IDs from Telegram text."""
        text = (value or "").strip()
        if not text:
            raise ValueError(f"Provide a {label} ID.")
        try:
            numeric_id = int(text)
        except ValueError as exc:
            raise ValueError(f"Invalid {label} ID: {text}") from exc
        if numeric_id <= 0:
            raise ValueError(f"{label.capitalize()} ID must be a positive integer.")
        return numeric_id

    def _parse_update_command_arguments(
        self, command_args: str, usage: str
    ) -> tuple[int, Dict[str, str]]:
        """Split '<id> field=value | field=value' into an ID and updates."""
        if not command_args:
            raise ValueError(usage)

        parts = command_args.split(maxsplit=1)
        if len(parts) < 2:
            raise ValueError(usage)

        record_id = self._parse_numeric_id(parts[0], "record")
        updates = self._parse_update_pairs(parts[1])
        return record_id, updates

    @staticmethod
    def _parse_update_pairs(payload: str) -> Dict[str, str]:
        """Parse 'field=value | field=value' payloads."""
        updates: Dict[str, str] = {}

        for chunk in (payload or "").split("|"):
            part = chunk.strip()
            if not part:
                continue
            if "=" not in part:
                raise ValueError(
                    "Each update must use field=value syntax. Separate multiple updates with |."
                )

            field, value = part.split("=", 1)
            field_name = field.strip()
            if not field_name:
                raise ValueError("Field names cannot be empty.")

            updates[field_name] = value.strip()

        if not updates:
            raise ValueError("No update values were provided.")

        return updates

    def _send_long_message(self, chat_id: int, text: str, max_length: int = 3500) -> None:
        """Send text while respecting Telegram message size limits."""
        remaining = text or ""

        while remaining:
            if len(remaining) <= max_length:
                self.bot.send_message(chat_id, remaining)
                break

            split_index = remaining.rfind("\n", 0, max_length)
            if split_index <= 0 or split_index < max_length // 2:
                split_index = max_length

            chunk = remaining[:split_index].rstrip()
            self.bot.send_message(chat_id, chunk)
            remaining = remaining[split_index:].lstrip()

    @staticmethod
    def _pick(row: Dict[str, Any], *keys: str, default: Any = "N/A") -> Any:
        for key in keys:
            if key in row and row[key] is not None and row[key] != "":
                return row[key]
        return default

    @staticmethod
    def _shorten(text: Any, limit: int = 120) -> str:
        if text is None:
            return "N/A"
        value = str(text).strip()
        if len(value) <= limit:
            return value
        return f"{value[:limit - 3]}..."

    @staticmethod
    def _format_price(value: Any) -> str:
        try:
            return f"${float(value):.2f}"
        except (TypeError, ValueError):
            return "N/A"

    @staticmethod
    def _format_value(value: Any) -> str:
        if value is None:
            return "null"
        return str(value)

    def _format_packs_response(self, packs: List[Dict[str, Any]]) -> str:
        if not packs:
            return "No rows found in table packs."

        max_items = 20
        lines = [f"Packs found: {len(packs)}"]

        for idx, pack in enumerate(packs[:max_items], start=1):
            pack_id = self._pick(pack, "id")
            title = self._pick(pack, "title")
            category = self._pick(pack, "category_name", default="Uncategorized")
            level = self._pick(pack, "level")
            status = self._pick(pack, "status")
            duration = self._pick(pack, "duration_hours", "durationHours")
            sale_price = self._format_price(
                self._pick(pack, "sale_price", "salePrice", default=None)
            )
            original_price = self._format_price(
                self._pick(pack, "original_price", "originalPrice", default=None)
            )
            description = self._shorten(self._pick(pack, "description", default="N/A"))

            lines.append(
                f"{idx}. [#{pack_id}] {title}\n"
                f"Category: {category} | Level: {level} | Status: {status}\n"
                f"Price: {sale_price} (original: {original_price}) | Duration hours: {duration}\n"
                f"Description: {description}"
            )

        if len(packs) > max_items:
            lines.append(f"... and {len(packs) - max_items} more packs.")

        return "\n\n".join(lines)

    def _format_pack_categories_response(self, categories: List[Dict[str, Any]]) -> str:
        if not categories:
            return "No rows found in table pack_categories."

        max_items = 25
        lines = [f"Pack categories found: {len(categories)}"]

        for idx, category in enumerate(categories[:max_items], start=1):
            category_id = self._pick(category, "id")
            name = self._pick(category, "name")
            status = self._pick(category, "status")
            icon = self._pick(category, "icon", default="-")
            description = self._shorten(self._pick(category, "description", default="N/A"))

            lines.append(
                f"{idx}. [#{category_id}] {name}\n"
                f"Status: {status} | Icon: {icon}\n"
                f"Description: {description}"
            )

        if len(categories) > max_items:
            lines.append(f"... and {len(categories) - max_items} more categories.")

        return "\n\n".join(lines)

    def _format_record_response(self, label: str, row: Dict[str, Any]) -> str:
        """Format every field in one record for admin inspection."""
        if not row:
            return f"{label} not found."

        record_id = self._pick(row, "id", default="?")
        lines = [f"{label} #{record_id}"]

        for key, value in row.items():
            lines.append(f"{key}: {self._format_value(value)}")

        return "\n".join(lines)

    def _format_fields_response(
        self, table_name: str, fields: List[Dict[str, Any]]
    ) -> str:
        """Show schema details so admins know which columns they can edit."""
        if not fields:
            return f"No fields were found for table {table_name}."

        lines = [f"Fields for {table_name}:"]
        for field in fields:
            edit_state = "editable" if field["updatable"] else "read-only"
            null_state = "nullable" if field["nullable"] else "required"
            lines.append(
                f"- {field['name']} ({field['column_type']}, {null_state}, {edit_state})"
            )

        return "\n".join(lines)

    def test_connection(self) -> bool:
        """Test if bot can connect to Telegram API."""
        print("\nTesting connection to Telegram API...")
        max_retries = 3
        retry_delay = 2

        for attempt in range(1, max_retries + 1):
            try:
                bot_info = self.bot.get_me()
                print(f"Connection successful! Bot: @{bot_info.username}")
                print(f"Bot ID: {bot_info.id}")
                return True
            except (ConnectionError, Timeout) as exc:
                print(
                    f"Connection attempt {attempt}/{max_retries} failed: {type(exc).__name__}"
                )
                if attempt < max_retries:
                    print(f"Retrying in {retry_delay} seconds...")
                    time.sleep(retry_delay)
                    retry_delay *= 2
                else:
                    print("Unable to connect to Telegram API after retries.")
                    print("Check internet access and bot token.")
                    return False
            except Exception as exc:
                print(f"Unexpected error: {exc}")
                return False

        return False

    def start(self) -> None:
        """Start the bot and keep it running."""
        print("Bot is starting...")

        if not self.test_connection():
            print("Cannot start bot without Telegram API connection.")
            return

        print("Press Ctrl+C to stop the bot")
        print("Waiting for incoming admin messages...\n")

        try:
            self.bot.infinity_polling()
        except KeyboardInterrupt:
            print("\nBot stopped by user.")
        except Exception as exc:
            print(f"\nError: {exc}")
            print(f"Error type: {type(exc).__name__}")
