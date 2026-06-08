"""
GROQ LLM Client - Handles communication with the GROQ API.

This module is responsible for:
1. Sending text to GROQ LLM
2. Receiving responses
3. Managing the connection
"""

from groq import Groq

from config.settings import Settings


class GroqClient:
    """Wrapper around Groq API for easy communication."""

    def __init__(self):
        """Initialize Groq client with API key."""
        self.client = Groq(api_key=Settings.GROQ_API_KEY)
        self.model = Settings.GROQ_MODEL

    def get_response(self, user_message: str) -> str:
        """
        Send a message to GROQ and get a response.

        Args:
            user_message: The text from the user

        Returns:
            str: The LLM response
        """
        try:
            system_prompt = (
                "You are the internal admin assistant for the YBrainy e-learning platform. "
                "You are speaking only with authorized platform administrators through Telegram. "
                "Be concise, operational, and helpful. Focus on packs, pack categories, "
                "courses, pricing, availability, certifications, and platform operations. "
                "If an admin asks for a database change, do not pretend you already changed "
                "MySQL unless the bot explicitly confirmed it. Instead, tell them to use the "
                "bot's update commands or provide the exact field values needed for the change. "
                "If a topic is unrelated to the platform or administration, politely steer the "
                "conversation back to platform work."
            )

            response = self.client.chat.completions.create(
                model=self.model,
                messages=[
                    {
                        "role": "system",
                        "content": system_prompt,
                    },
                    {
                        "role": "user",
                        "content": user_message,
                    },
                ],
                max_tokens=1024,
                temperature=0.7,
            )

            return response.choices[0].message.content

        except Exception as exc:
            return f"Error getting LLM response: {exc}"
