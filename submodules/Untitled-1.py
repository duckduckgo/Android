
# IMPORTS
# ==========================
import requests
import pandas as pd
import ta
import logging

from telegram import Update
from telegram.ext import (
        ApplicationBuilder,
            CommandHandler,
                ContextTypes,
)

# ==========================
# CONFIGURACIÓN alerta  de señales de trading ACTIVADO/pocket option 
# ==========================
TOKEN = "TU_TOKEN_AQUI"8313904219:AAGrk7sDzZewAd43MJMoGHbDRB06qGzDoDs
CHAT_ID = 8313904219  # TU CHAT ID

SYMBOL = "BTCUSDT"
INTERVAL = "15m"  # 1m, 5m, 15m, 1h, etc.

RSI_BUY = 30
RSI_SELL = 70

CHECK_INTERVAL = 900  # segundos (15 min)

# ==========================
# LOGGING
# ==========================
logging.basicConfig(
        format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
            level=logging.INFO
)

# ==========================
# DATOS DE MERCADO
# ==========================
def get_market_data() -> pd.DataFrame:
    url = "https://api.binance.com/api/v3/klines"
        params = {
                    "symbol": SYMBOL,
                            "interval": INTERVAL,
                                    "limit": 100
        }

            response = requests.get(url, params=params)
                response.raise_for_status()
                    data = response.json()

                        df = pd.DataFrame(data, columns=[
                                    "open_time", "open", "high", "low", "close",
                                            "volume", "close_time", "qav",
                                                    "num_trades", "taker_base_vol",
                                                            "taker_quote_vol", "ignore"
                        ])

                            df["close"] = df["close"].astype(float)
                                return df

                                # ==========================
                                # ESTRATEGIA DE TRADING
                                # ==========================
                                def generate_signal(df: pd.DataFrame) -> str | None:
                                    df["rsi"] = ta.momentum.RSIIndicator(
                                                close=df["close"], window=14
                                    ).rsi()

                                        df["ema"] = ta.trend.EMAIndicator(
                                                    close=df["close"], window=50
                                        ).ema_indicator()

                                            last = df.iloc[-1]

                                                if last["rsi"] < RSI_BUY and last["close"] > last["ema"]:
                                                        return "BUY"

                                                            if last["rsi"] > RSI_SELL and last["close"] < last["ema"]:
                                                                    return "SELL"

                                                                        return None

                                                                        # ==========================
                                                                        # TELEGRAM/Henbot4_bot
                                                                        # ==========================
                                                                        async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
                                                                            await update.message.reply_text(
                                                                                        "🤖 Bot de señales de trading ACTIVADO\n"
                                                                                                f"Par: {SYMBOL}\n"
                                                                                                        f"Temporalidad:1miunto {INTERVAL}"
                                                                            )

                                                                            async def send_signal(context: ContextTypes.DEFAULT_TYPE):
                                                                                try:
                                                                                        df = get_market_data()
                                                                                                signal = generate_signal(df)

                                                                                                        if not signal:
                                                                                                                    return

                                                                                                                            last_signal =eur usd otc context.bot_data.get("last_signal")

                                                                                                                                    # Evita enviar la misma señal repetida 
                                                                                                                                            if signal == last_signal:
                                                                                                                                                        return

                                                                                                                                                                context.bot_data["last_signal"] = signal

                                                                                                                                                                        price = df.iloc[-1]["close"]
                                                                                                                                                                                rsi = round(df.iloc[-1]["rsi"], 2)

                                                                                                                                                                                        message = (
                                                                                                                                                                                                        f"📊 *SEÑAL DE TRADING*\n\n"
                                                                                                                                                                                                                    f"🪙 Par: `{SYMBOL}`\n"
                                                                                                                                                                                                                                f"⏱ Temporalidad: `{INTERVAL}`\n"
                                                                                                                                                                                                                                            f"📈 Tipo: *{signal}*\n"
                                                                                                                                                                                                                                                        f"💰 Precio: `{price}`\n"
                                                                                                                                                                                                                                                                    f"📉 RSI: `{rsi}`"
                                                                                                                                                                                        )

                                                                                                                                                                                                await context.bot.send_message(
                                                                                                                                                                                                                chat_id=CHAT_ID,
                                                                                                                                                                                                                            text=message,
                                                                                                                                                                                                                                        parse_mode="Markdown"
                                                                                                                                                                                                )

                                                                                                                                                                                                    except Exception as e:
                                                                                                                                                                                                            logging.error(f"Error en send_signal: {e}")

                                                                                                                                                                                                            # ==========================
                                                                                                                                                                                                            # MAIN
                                                                                                                                                                                                            # ==========================
                                                                                                                                                                                                            def main():
                                                                                                                                                                                                                app = ApplicationBuilder().token(TOKEN).build()

                                                                                                                                                                                                                    app.add_handler(CommandHandler("start", start))

                                                                                                                                                                                                                        app.job_queue.run_repeating(
                                                                                                                                                                                                                                    send_signal,
                                                                                                                                                                                                                                            interval=CHECK_INTERVAL,
                                                                                                                                                                                                                                                    first=10
                                                                                                                                                                                                                        )

                                                                                                                                                                                                                            print("🤖 Bot de trading en ejecución...")
                                                                                                                                                                                                                                app.run_polling()

                                                                                                                                                                                                                                if __name__ == "__main__":
                                                                                                                                                                                                                                    main()
                                                                                                                                                                                                                        )
                                                                                                                                                                                                )
                                                                                                                                                                                        )
                                                                            )
                                        )
                                    )
                        ])
        }
)
)