"""
Network Diagnostic Tool - Helps troubleshoot connection issues.

Run this script to diagnose network connectivity problems.
"""

import socket
import sys
from urllib.request import urlopen
from urllib.error import URLError, HTTPError


def test_internet_connection():
    """Test if you have internet connection."""
    print("🔍 Testing internet connection...")
    try:
        # Try to reach Google DNS
        socket.create_connection(("8.8.8.8", 53), timeout=3)
        print("✅ Internet connection: OK")
        return True
    except (socket.timeout, socket.error):
        print("❌ Internet connection: FAILED")
        print("   Cannot reach internet. Check your network connection.")
        return False


def test_dns_resolution():
    """Test if DNS resolution works."""
    print("\n🔍 Testing DNS resolution for api.telegram.org...")
    try:
        ip = socket.gethostbyname("api.telegram.org")
        print(f"✅ DNS resolution: OK")
        print(f"   api.telegram.org resolves to: {ip}")
        return True
    except socket.gaierror:
        print("❌ DNS resolution: FAILED")
        print("   Cannot resolve api.telegram.org. Check your DNS settings.")
        return False


def test_telegram_endpoint():
    """Test if we can reach Telegram API endpoint."""
    print("\n🔍 Testing connection to api.telegram.org...")
    try:
        response = urlopen("https://api.telegram.org/", timeout=10)
        print("✅ Telegram API endpoint: REACHABLE")
        return True
    except (HTTPError, URLError) as e:
        if "timed out" in str(e).lower() or "connection" in str(e).lower():
            print("❌ Telegram API endpoint: NOT REACHABLE (timeout/connection error)")
            print(f"   Error: {e}")
            return False
        else:
            print("⚠️  Telegram API endpoint: Partial response")
            print(f"   Status: {e}")
            return True


def test_firewall():
    """Test if firewall might be blocking connections."""
    print("\n🔍 Testing for firewall/proxy issues...")
    print("   Trying to connect to Telegram API on port 443...")
    try:
        socket.create_connection(("api.telegram.org", 443), timeout=5)
        print("✅ Port 443 (HTTPS): OPEN")
        return True
    except socket.timeout:
        print("❌ Port 443 (HTTPS): TIMEOUT")
        print("   Your firewall or network might be blocking HTTPS connections")
        print("   Or Telegram API servers might be unreachable")
        return False
    except (socket.error, OSError):
        print("❌ Port 443 (HTTPS): UNREACHABLE")
        print("   Firewall or network is likely blocking HTTPS connections")
        return False


def main():
    """Run all diagnostics."""
    print("=" * 60)
    print("🔧 Telegram Bot Network Diagnostic Tool")
    print("=" * 60)
    print()
    
    results = {
        "Internet": test_internet_connection(),
        "DNS": test_dns_resolution(),
        "Firewall": test_firewall(),
        "Telegram": test_telegram_endpoint(),
    }
    
    print("\n" + "=" * 60)
    print("📊 Summary")
    print("=" * 60)
    
    for test, result in results.items():
        status = "✅ PASS" if result else "❌ FAIL"
        print(f"{test:15} {status}")
    
    print("\n" + "=" * 60)
    
    if all(results.values()):
        print("✅ All tests passed! Your network is ready.")
        print("\nIf the bot still won't start, check:")
        print("  1. Your bot token in .env is correct")
        print("  2. The bot token hasn't been invalidated")
        print("  3. Try restarting the bot")
    else:
        print("⚠️  Some tests failed. Possible solutions:")
        
        if not results["Internet"]:
            print("\n  Internet Connection:")
            print("    • Check your WiFi/Ethernet connection")
            print("    • Restart your router")
            print("    • Check if your ISP is having issues")
        
        if not results["DNS"]:
            print("\n  DNS Issues:")
            print("    • Check your DNS settings (Settings > Network)")
            print("    • Try using Google DNS: 8.8.8.8 and 8.8.4.4")
        
        if not results["Firewall"]:
            print("\n  Firewall/Network Issues:")
            print("    • Check your firewall settings")
            print("    • If using VPN, try disabling it")
            print("    • If in corporate network, check proxy settings")
            print("    • Contact your network administrator")
        
        if not results["Telegram"]:
            print("\n  Telegram API Issues:")
            print("    • Try again in a few minutes")
            print("    • Check Telegram's status page")
            print("    • Try using a different internet connection")
    
    print()
    return 0 if all(results.values()) else 1


if __name__ == "__main__":
    sys.exit(main())
