"""
Generate cute child-voice result lines for the Roshambo app with edge-tts.

Voice : zh-CN-XiaomengNeural (cartoon / child voice — the cutest zh-CN voice)
Emotion is conveyed via the Communicate rate/pitch params (every edge-tts voice
supports these, unlike express-as styles that only some voices honour):
  - win  : faster + higher  -> bright, cheerful
  - lose : slower + lower   -> soft, comforting
  - draw : near-neutral     -> calm

Output: 16-bit mono PCM @ 44.1 kHz WAV, overwriting app/src/main/res/raw/voice_*.wav
"""

import asyncio
import miniaudio
import os
import wave

import edge_tts

VOICE = "zh-CN-XiaomengNeural"
OUT_DIR = "app/src/main/res/raw"

# Reuse the sandbox proxy so edge-tts can reach the TTS endpoint. Leave empty
# to connect directly (the sandbox transparent egress reaches Microsoft TTS).
PROXY = (
    os.environ.get("EDGE_TTS_PROXY")
    or os.environ.get("HTTP_PROXY")
    or os.environ.get("HTTPS_PROXY")
    or None
)

# (event, spoken text, rate, pitch)  -- pitch uses Hz (SSML pitch format)
LINES = [
    ("win",  "你赢啦！", "+18%", "+12Hz"),
    ("lose", "你输啦！", "-12%", "-10Hz"),
    ("draw", "平局啦",  "+2%",  "0Hz"),
]


async def synth(text: str, rate: str, pitch: str) -> bytes:
    communicate = edge_tts.Communicate(
        text=text, voice=VOICE, rate=rate, pitch=pitch, proxy=PROXY
    )
    chunks = []
    # edge-tts >=7 yields dicts {"type": "audio"|"wordBoundary"|..., "data": bytes}
    async for message in communicate.stream():
        if message.get("type") == "audio":
            chunks.append(message["data"])
    encoded = b"".join(chunks)
    decoded = miniaudio.decode(
        encoded,
        output_format=miniaudio.SampleFormat.SIGNED16,
        nchannels=1,
        sample_rate=44100,
    )
    return decoded.samples


def main() -> None:
    for event, text, rate, pitch in LINES:
        samples = asyncio.run(synth(text, rate, pitch))
        path = f"{OUT_DIR}/voice_{event}.wav"
        with wave.open(path, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)  # 16-bit
            w.setframerate(44100)
            w.writeframes(samples)
        secs = len(samples) / 2 / 44100
        print(f"wrote {path}: {secs:.2f}s, {len(samples)} bytes")


if __name__ == "__main__":
    main()
