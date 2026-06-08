"""
Agent Logic - The "brain" of the chatbot.

This module is responsible for:
1. Receiving user input
2. Calling the LLM
3. Returning formatted responses
"""

from llm.groq_client import GroqClient


class ChatAgent:
    """The intelligent agent that processes user messages."""

    def __init__(self):
        """Initialize the agent with an LLM client."""
        self.llm = GroqClient()

    def process_message(self, user_message: str) -> str:
        """
        Process a user message and return an intelligent response.

        Args:
            user_message: The text message from the user

        Returns:
            str: The agent's response
        """
        # Step 1: Validate input
        if not user_message or not user_message.strip():
            return "Please send me a message!"

        # Step 2: Send to LLM
        response = self.llm.get_response(user_message)

        # Step 3: Return response
        return response
