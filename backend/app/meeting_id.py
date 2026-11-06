"""Meeting IDs look like ``abc-defg-hjk``.

The first 7 letters are random, the last 3 are an HMAC of them. The server can
check that an ID was issued by it without storing anything, so a link keeps
working after the meeting closed: the room is simply created again on join.
"""

import hashlib
import hmac
import re
import secrets

# No "l" — it is easy to confuse with "i" and "1" when typing a code by hand.
ALPHABET = "abcdefghijkmnopqrstuvwxyz"
_RANDOM_LEN = 7
_SIG_LEN = 3
_ID_RE = re.compile(rf"^[{ALPHABET}]{{3}}-[{ALPHABET}]{{4}}-[{ALPHABET}]{{3}}$")


def _signature(random_part: str, secret: str) -> str:
    digest = hmac.new(secret.encode(), random_part.encode(), hashlib.sha256).digest()
    return "".join(ALPHABET[b % len(ALPHABET)] for b in digest[:_SIG_LEN])


def _format(raw: str) -> str:
    return f"{raw[:3]}-{raw[3:7]}-{raw[7:]}"


def new_meeting_id(secret: str) -> str:
    random_part = "".join(secrets.choice(ALPHABET) for _ in range(_RANDOM_LEN))
    return _format(random_part + _signature(random_part, secret))


def normalize(meeting_id: str) -> str:
    return meeting_id.strip().lower()


def is_valid(meeting_id: str, secret: str) -> bool:
    if not _ID_RE.match(meeting_id):
        return False
    raw = meeting_id.replace("-", "")
    random_part, sig = raw[:_RANDOM_LEN], raw[_RANDOM_LEN:]
    return hmac.compare_digest(sig, _signature(random_part, secret))
