# Telegram Bot Troubleshooting Guide

## Connection Timeout Error

If you're seeing this error:
```
TimeoutError: timed out
Connection to api.telegram.org timed out. (connect timeout=15)
```

This means your bot **cannot reach Telegram's API servers**. Here's how to fix it:

---

## Step 1: Run Diagnostics

First, diagnose your network:

```bash
python diagnose.py
```

This will test your:
- Internet connection
- DNS resolution
- Firewall/Proxy settings
- Connection to Telegram API

Review the results and follow the recommendations.

---

## Common Causes & Solutions

### 1. **No Internet Connection**
- Check if your WiFi/Ethernet is connected
- Try opening a website in your browser
- Restart your router

### 2. **Firewall/Network Blocking**
- Your firewall might be blocking HTTPS connections to Telegram
- Solutions:
  - Add Telegram API to firewall whitelist
  - Try disabling your antivirus temporarily
  - If using VPN, try disabling it
  - If in corporate network, contact IT support

### 3. **Proxy/Corporate Network**
- Some corporate networks use proxies
- The bot might not be configured to use your proxy
- Solutions:
  - Test from a personal network (mobile hotspot)
  - Contact your network administrator
  - Ask about proxy settings

### 4. **Invalid Bot Token**
- Your `.env` file might have an incorrect token
- Solutions:
  - Check `.env` file for typos
  - Get a new token from BotFather on Telegram
  - Paste the full token without extra spaces

### 5. **Telegram Servers Down** (Rare)
- Check [Telegram's status](https://telegram.org/)
- Try again in a few minutes
- Check Telegram's X/Twitter for status updates

---

## Advanced Troubleshooting

### Test with curl
If diagnostics pass but bot still won't work, test with curl:

```powershell
# Replace YOUR_TOKEN with your actual bot token
curl -v "https://api.telegram.org/botYOUR_TOKEN/getMe"
```

You should see a response like:
```json
{"ok":true,"result":{"id":123456,"is_bot":true,"first_name":"MyBot",...}}
```

If this fails, it's a network/firewall issue.

### Check DNS
```powershell
nslookup api.telegram.org
```

Should show an IP address like `149.154.167.190`

### Check Network Connectivity
```powershell
ping api.telegram.org
```

Should show successful pings. If "timed out" or "unreachable", it's a network issue.

---

## Network Configuration Solutions

### For Windows Firewall
1. Open Windows Defender Firewall
2. Click "Allow an app through firewall"
3. Make sure your Python executable is allowed

### For Corporate/VPN Networks
The bot automatically uses shorter timeouts. If still failing:
1. Try from a personal network first (mobile hotspot)
2. Check if your VPN is blocking Telegram
3. Contact IT if Telegram is blocked by policy

### For Router/ISP Issues
1. Check if your ISP blocks Telegram (some countries do)
2. Try using different DNS:
   - Google DNS: `8.8.8.8` and `8.8.4.4`
   - Cloudflare DNS: `1.1.1.1` and `1.0.0.1`

---

## If Everything Fails

1. Try from a **different network** (mobile hotspot, café WiFi)
2. If it works elsewhere, it's your network/ISP
3. If it fails everywhere:
   - Check your `.env` file for the token
   - Make sure you're using a valid Telegram bot token
   - Create a new bot with BotFather and use that token

---

## Getting Help

When reporting issues, include:
1. Output from `diagnose.py`
2. Your OS and Python version: `python --version`
3. Your bot token (first 10 characters only): `botXXXXXXX...`
4. Whether you're on a corporate/VPN network
5. Any error messages from the bot

**Never share your full bot token!**

---

## Updated Bot Features

Your bot now has:
- ✅ Connection testing before startup
- ✅ Retry logic with exponential backoff
- ✅ Better timeout configuration
- ✅ Clearer error messages
- ✅ Automatic restart on failures

Run the improved bot:
```bash
python main.py
```

The bot will test your connection and provide better error diagnostics if something goes wrong.
