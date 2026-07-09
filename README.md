# VideoCharter Bot

Inline-first Telegram bot for quick знакомства with a clean chat UI:

- all menu actions use inline buttons
- the main control message is edited instead of spamming new messages
- profile media cards are deleted when the user moves forward
- free daily browsing limits and ad interstitials follow the provided rules
- mutual likes create a match notification
- country list is sorted dynamically by real selection popularity

## Features

- profile creation and rebuild flow
- media rules: up to 3 photos, or 1 video + 2 photos
- privacy mode for username visibility
- browsing with `Like`, `Skip`, `Back`, `Report`
- incoming likes inbox
- mutual match notifications
- report flow with reason + text/media evidence
- moderation and moderator management
- Telegram Stars subscription screen for disabling ads
- Adsgram bot ads after the free daily limit, with a fallback interstitial if Adsgram is not configured
- JSON persistence in a mounted data directory

## Environment variables

- `TELEGRAM_BOT_TOKEN` - required
- `TELEGRAM_BOT_USERNAME` - required
- `ADMIN_IDS` - optional comma-separated Telegram user ids with admin access
- `DATA_FILE` - optional, default `data/state.json`
- `MIN_ACTION_INTERVAL_MS` - optional, default `700`
- `ADSGRAM_ENABLED` - optional, auto-detects from Adsgram token + block ids when omitted
- `ADSGRAM_TOKEN` - optional Adsgram publisher token from your Adsgram cabinet
- `ADSGRAM_BLOCK_ID` / `ADSGRAM_BLOCK_IDS` - optional Adsgram bot block id(s), digits only or `bot-123` format
- `ADSGRAM_LANGUAGE` - optional fallback ad language, default auto / `en`
- `ADSGRAM_CANDIDATES_PER_BLOCK` - optional, default `2`

## Local run

```bash
mvn test
mvn package
TELEGRAM_BOT_TOKEN=your_token \
TELEGRAM_BOT_USERNAME=your_bot_username \
ADMIN_IDS=123456789 \
ADSGRAM_ENABLED=true \
ADSGRAM_TOKEN=your_adsgram_token \
ADSGRAM_BLOCK_ID=123456 \
java -jar target/videocharter-bot-1.0.0.jar
```

## Docker build

```bash
docker build -t videocharter-bot .
```

## Docker run

```bash
docker run -d \
  --name videocharter-bot \
  -e TELEGRAM_BOT_TOKEN=your_token \
  -e TELEGRAM_BOT_USERNAME=your_bot_username \
  -e ADMIN_IDS=123456789 \
  -e DATA_FILE=/app/data/state.json \
  -e ADSGRAM_ENABLED=true \
  -e ADSGRAM_TOKEN=your_adsgram_token \
  -e ADSGRAM_BLOCK_ID=123456 \
  -v "$(pwd)/data:/app/data" \
  --restart unless-stopped \
  videocharter-bot
```

## Notes

- The interface is intentionally English-only, per the specification.
- Clean chat behavior is implemented on a best-effort basis through message editing and deletion.
- Adsgram block ids are normalized automatically; both `123456` and `bot-123456` are accepted.
- For production, mount the `data` volume and keep bot token/admin ids outside the image.
