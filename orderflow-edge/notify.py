"""Send today's paper-trading summary by email and/or WhatsApp. Each channel is used only if its secrets are set.

Email (Gmail):  SMTP_USER (your gmail), SMTP_PASS (a Gmail *app password*), EMAIL_TO (where to send)
WhatsApp:       WHATSAPP_PHONE (with country code, e.g. 919876543210), WHATSAPP_APIKEY (from CallMeBot)
"""
import os
import smtplib
import sys
import urllib.parse
import urllib.request
from email.message import EmailMessage
from pathlib import Path


def latest_block(path: Path) -> str:
    text = path.read_text() if path.exists() else ""
    block = text.split("=====")[-1]
    lines = [l.rstrip() for l in block.splitlines()]
    # Keep the signal table and the paper result; drop the per-coin download lines.
    keep = [l for l in lines if l and not l.endswith(("days", "missing")) and "→" not in l and not l.startswith("   skipped")]
    return "\n".join(keep[-20:]) or "No result today (see the GitHub Actions run)."


def summary(block: str) -> tuple[str, str]:
    actions = [l for l in block.splitlines() if " BUY " in l or " SELL " in l]
    paper = next((l for l in block.splitlines() if l.startswith("Paper trading")), "")
    subject = f"Paper trading: {len(actions)} change(s)" if actions else "Paper trading: no changes today"
    short = "\n".join(["X11 paper trading", *(actions or ["No buys or sells today."]), paper]).strip()
    return subject, short


def main() -> None:
    block = latest_block(Path(sys.argv[1] if len(sys.argv) > 1 else "paper_daily.txt"))
    subject, short = summary(block)
    env = os.environ.get
    sent = []
    if env("SMTP_USER") and env("SMTP_PASS") and env("EMAIL_TO"):
        m = EmailMessage()
        m["Subject"], m["From"], m["To"] = subject, env("SMTP_USER"), env("EMAIL_TO")
        m.set_content(block + "\n\nNot financial advice. Paper trading only.")
        with smtplib.SMTP_SSL(env("SMTP_HOST", "smtp.gmail.com"), 465, timeout=30) as s:
            s.login(env("SMTP_USER"), env("SMTP_PASS"))
            s.send_message(m)
        sent.append("email")
    if env("WHATSAPP_PHONE") and env("WHATSAPP_APIKEY"):
        q = urllib.parse.urlencode({"phone": env("WHATSAPP_PHONE"), "text": short, "apikey": env("WHATSAPP_APIKEY")})
        with urllib.request.urlopen(f"https://api.callmebot.com/whatsapp.php?{q}", timeout=30) as r:
            r.read()
        sent.append("whatsapp")
    print("notified: " + (", ".join(sent) if sent else "nothing configured (add the secrets to enable)"))


if __name__ == "__main__":
    main()
