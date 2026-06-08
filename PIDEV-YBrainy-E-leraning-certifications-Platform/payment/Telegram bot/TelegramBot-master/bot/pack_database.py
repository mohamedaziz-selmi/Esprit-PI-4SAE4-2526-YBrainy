"""
MySQL access for Telegram pack commands.
"""

from __future__ import annotations

from decimal import Decimal, InvalidOperation
from typing import Any, Dict, List, Sequence

import mysql.connector
from mysql.connector import Error

from config.settings import Settings


class PackDatabaseService:
    """Reads and updates pack and pack category data in MySQL."""

    TABLE_ALIASES = {
        "pack": "packs",
        "packs": "packs",
        "category": "pack_categories",
        "categories": "pack_categories",
        "pack_category": "pack_categories",
        "pack_categories": "pack_categories",
    }
    INTEGER_TYPES = {
        "bigint",
        "int",
        "integer",
        "mediumint",
        "smallint",
        "tinyint",
    }
    DECIMAL_TYPES = {"decimal", "double", "float", "numeric", "real"}
    BOOLEAN_TYPES = {"bit", "bool", "boolean"}

    def __init__(self) -> None:
        self._config = {
            "host": Settings.DB_HOST,
            "port": Settings.DB_PORT,
            "user": Settings.DB_USER,
            "password": Settings.DB_PASSWORD,
            "database": Settings.DB_NAME,
        }
        self._column_cache: Dict[str, List[Dict[str, Any]]] = {}

    def fetch_packs(self) -> List[Dict[str, Any]]:
        """
        Fetch packs with category name.

        Uses p.* to stay resilient to schema naming differences.
        """
        query = """
            SELECT p.*, c.name AS category_name
            FROM packs p
            LEFT JOIN pack_categories c ON p.category_id = c.id
            ORDER BY p.id DESC
        """
        return self._fetch_all(query)

    def fetch_pack(self, pack_id: int) -> Dict[str, Any] | None:
        """Fetch a single pack by ID."""
        query = """
            SELECT p.*, c.name AS category_name
            FROM packs p
            LEFT JOIN pack_categories c ON p.category_id = c.id
            WHERE p.id = %s
        """
        return self._fetch_one(query, (pack_id,))

    def fetch_pack_categories(self) -> List[Dict[str, Any]]:
        """Fetch all pack categories."""
        query = """
            SELECT *
            FROM pack_categories
            ORDER BY id DESC
        """
        return self._fetch_all(query)

    def fetch_pack_category(self, category_id: int) -> Dict[str, Any] | None:
        """Fetch a single pack category by ID."""
        query = """
            SELECT *
            FROM pack_categories
            WHERE id = %s
        """
        return self._fetch_one(query, (category_id,))

    def fetch_table_columns(self, table_name: str) -> List[Dict[str, Any]]:
        """Return schema metadata for the requested table."""
        normalized_table = self._normalize_table_name(table_name)
        if normalized_table not in self._column_cache:
            query = """
                SELECT
                    COLUMN_NAME,
                    DATA_TYPE,
                    COLUMN_TYPE,
                    IS_NULLABLE,
                    COLUMN_KEY,
                    EXTRA
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = %s
                  AND TABLE_NAME = %s
                ORDER BY ORDINAL_POSITION
            """
            rows = self._fetch_all(query, (Settings.DB_NAME, normalized_table))
            if not rows:
                raise ValueError(f"Table '{normalized_table}' was not found in the database.")

            self._column_cache[normalized_table] = [
                {
                    "name": row["COLUMN_NAME"],
                    "data_type": str(row["DATA_TYPE"]).lower(),
                    "column_type": str(row["COLUMN_TYPE"]).lower(),
                    "nullable": str(row["IS_NULLABLE"]).upper() == "YES",
                    "key": row["COLUMN_KEY"],
                    "extra": str(row["EXTRA"]).lower(),
                    "updatable": self._is_updatable_column(row),
                }
                for row in rows
            ]

        return list(self._column_cache[normalized_table])

    def update_pack(self, pack_id: int, updates: Dict[str, Any]) -> Dict[str, Any]:
        """Update one pack row and return the fresh record."""
        return self._update_record("packs", pack_id, updates)

    def update_pack_category(
        self, category_id: int, updates: Dict[str, Any]
    ) -> Dict[str, Any]:
        """Update one pack category row and return the fresh record."""
        return self._update_record("pack_categories", category_id, updates)

    def _normalize_table_name(self, table_name: str) -> str:
        normalized = (table_name or "").strip().lower()
        if normalized not in self.TABLE_ALIASES:
            raise ValueError(
                "Unsupported table. Use 'packs' or 'pack_categories'."
            )
        return self.TABLE_ALIASES[normalized]

    @staticmethod
    def _is_updatable_column(row: Dict[str, Any]) -> bool:
        """Exclude primary keys and generated columns from updates."""
        column_name = str(row["COLUMN_NAME"]).lower()
        extra = str(row["EXTRA"]).lower()
        return column_name != "id" and "generated" not in extra and "auto_increment" not in extra

    def _update_record(
        self, table_name: str, record_id: int, updates: Dict[str, Any]
    ) -> Dict[str, Any]:
        normalized_table = self._normalize_table_name(table_name)
        columns = self.fetch_table_columns(normalized_table)
        column_lookup = {column["name"].lower(): column for column in columns}

        if not updates:
            raise ValueError("No updates were provided.")

        prepared_updates: Dict[str, Any] = {}
        invalid_fields: List[str] = []
        locked_fields: List[str] = []

        for raw_field, raw_value in updates.items():
            field_name = str(raw_field).strip()
            if not field_name:
                invalid_fields.append(field_name)
                continue

            column = column_lookup.get(field_name.lower())
            if column is None:
                invalid_fields.append(field_name)
                continue
            if not column["updatable"]:
                locked_fields.append(column["name"])
                continue

            prepared_updates[column["name"]] = self._coerce_value(raw_value, column)

        if invalid_fields:
            invalid_list = ", ".join(sorted({field for field in invalid_fields if field}))
            if invalid_list:
                raise ValueError(f"Unknown field(s): {invalid_list}")
            raise ValueError("One or more update fields were blank.")
        if locked_fields:
            locked_list = ", ".join(sorted(set(locked_fields)))
            raise ValueError(f"Field(s) cannot be updated: {locked_list}")
        if not prepared_updates:
            raise ValueError("No valid fields were provided for update.")

        assignments = ", ".join(f"`{field}` = %s" for field in prepared_updates)
        params = list(prepared_updates.values()) + [record_id]
        query = f"UPDATE `{normalized_table}` SET {assignments} WHERE id = %s"
        self._execute(query, params)

        updated_record = self._fetch_record_by_table(normalized_table, record_id)
        if updated_record is None:
            raise LookupError(
                f"No {self._table_label(normalized_table)} found with id {record_id}."
            )
        return updated_record

    def _fetch_record_by_table(
        self, table_name: str, record_id: int
    ) -> Dict[str, Any] | None:
        if table_name == "packs":
            return self.fetch_pack(record_id)
        if table_name == "pack_categories":
            return self.fetch_pack_category(record_id)
        raise ValueError(f"Unsupported table '{table_name}'.")

    @staticmethod
    def _table_label(table_name: str) -> str:
        if table_name == "packs":
            return "pack"
        if table_name == "pack_categories":
            return "pack category"
        return table_name

    def _coerce_value(self, raw_value: Any, column: Dict[str, Any]) -> Any:
        """Convert Telegram text into a sensible MySQL value."""
        text = "" if raw_value is None else str(raw_value).strip()
        normalized = text.lower()
        field_name = str(column["name"]).lower()

        if normalized in {"null", "none"}:
            if column["nullable"]:
                return None
            raise ValueError(f"Field '{column['name']}' cannot be null.")

        data_type = column["data_type"]
        column_type = column["column_type"]

        if data_type in self.BOOLEAN_TYPES or column_type.startswith("tinyint(1)"):
            boolean_map = {
                "0": 0,
                "1": 1,
                "false": 0,
                "no": 0,
                "true": 1,
                "yes": 1,
            }
            if normalized not in boolean_map:
                raise ValueError(
                    f"Field '{column['name']}' expects true/false, yes/no, or 1/0."
                )
            return boolean_map[normalized]

        if data_type in self.INTEGER_TYPES:
            try:
                return int(text)
            except ValueError as exc:
                raise ValueError(
                    f"Field '{column['name']}' expects an integer value."
                ) from exc

        if data_type in self.DECIMAL_TYPES:
            try:
                return Decimal(text)
            except InvalidOperation as exc:
                raise ValueError(
                    f"Field '{column['name']}' expects a numeric value."
                ) from exc

        if field_name in {"status", "level"}:
            return text.upper()

        return text

    def _fetch_all(
        self, query: str, params: Sequence[Any] | None = None
    ) -> List[Dict[str, Any]]:
        connection = None
        cursor = None
        try:
            connection = mysql.connector.connect(**self._config)
            cursor = connection.cursor(dictionary=True)
            cursor.execute(query, params or ())
            return cursor.fetchall()
        except Error as exc:
            raise RuntimeError(f"MySQL error: {exc}") from exc
        finally:
            if cursor is not None:
                cursor.close()
            if connection is not None and connection.is_connected():
                connection.close()

    def _fetch_one(
        self, query: str, params: Sequence[Any] | None = None
    ) -> Dict[str, Any] | None:
        rows = self._fetch_all(query, params)
        return rows[0] if rows else None

    def _execute(self, query: str, params: Sequence[Any] | None = None) -> int:
        connection = None
        cursor = None
        try:
            connection = mysql.connector.connect(**self._config)
            cursor = connection.cursor()
            cursor.execute(query, params or ())
            connection.commit()
            return cursor.rowcount
        except Error as exc:
            raise RuntimeError(f"MySQL error: {exc}") from exc
        finally:
            if cursor is not None:
                cursor.close()
            if connection is not None and connection.is_connected():
                connection.close()
