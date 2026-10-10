"""
Generate high-quality synthesized sound effects for the Roshambo app.
Uses scipy to create clean, professional audio with proper ADSR envelopes.

Output: 16-bit mono PCM @ 48 kHz WAV (higher quality than original 44.1 kHz)
"""

import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, filtfilt, resample_poly
import os

OUT_DIR = "app/src/main/res/raw"
SAMPLE_RATE = 48000  # Higher than original 44.1kHz for better clarity

def normalize(data: np.ndarray) -> np.ndarray:
    """Normalize to [-1, 1] range."""
    max_val = np.max(np.abs(data))
    if max_val > 0:
        data = data / max_val
    return data

def apply_adsr(data: np.ndarray, attack: float, decay: float, sustain: float, release: float, sr: int) -> np.ndarray:
    """Apply ADSR envelope to audio data."""
    total_samples = len(data)
    attack_samples = int(attack * sr)
    decay_samples = int(decay * sr)
    release_samples = int(release * sr)
    
    # Ensure we don't exceed total samples
    max_envelope = total_samples - attack_samples - decay_samples
    if release_samples > max_envelope:
        release_samples = max(0, max_envelope)
    
    sustain_samples = total_samples - attack_samples - decay_samples - release_samples
    if sustain_samples < 0:
        sustain_samples = 0
    
    envelope = np.zeros(total_samples)
    
    # Attack
    if attack_samples > 0:
        envelope[:attack_samples] = np.linspace(0, 1, attack_samples)
    
    # Decay
    if decay_samples > 0:
        envelope[attack_samples:attack_samples + decay_samples] = np.linspace(1, sustain, decay_samples)
    
    # Sustain
    if sustain_samples > 0:
        envelope[attack_samples + decay_samples:attack_samples + decay_samples + sustain_samples] = sustain
    
    # Release
    if release_samples > 0:
        envelope[-release_samples:] = np.linspace(sustain, 0, release_samples)
    
    return data * envelope

def generate_tone(freq: float, duration: float, sr: int, harmonics: list = None) -> np.ndarray:
    """Generate a tone with optional harmonics."""
    t = np.linspace(0, duration, int(sr * duration), False)
    data = np.sin(2 * np.pi * freq * t)
    
    if harmonics:
        for harmonic_freq, harmonic_amp in harmonics:
            data += harmonic_amp * np.sin(2 * np.pi * harmonic_freq * t)
    
    return data

def generate_tap(sr: int) -> np.ndarray:
    """Short tap/click sound."""
    duration = 0.08
    data = generate_tone(800, duration, sr, [(1600, 0.3), (2400, 0.1)])
    data = apply_adsr(data, 0.005, 0.02, 0.3, 0.05, sr)
    return normalize(data)

def generate_tick(sr: int) -> np.ndarray:
    """Countdown tick sound."""
    duration = 0.1
    data = generate_tone(1000, duration, sr, [(2000, 0.2)])
    data = apply_adsr(data, 0.005, 0.03, 0.2, 0.06, sr)
    return normalize(data)

def generate_go(sr: int) -> np.ndarray:
    """Go! sound - ascending arpeggio."""
    duration = 0.3
    t = np.linspace(0, duration, int(sr * duration), False)
    
    # Ascending notes: C4 -> E4 -> G4 -> C5
    freqs = [261.63, 329.63, 392.00, 523.25]
    note_duration = duration / len(freqs)
    
    data = np.zeros(int(sr * duration))
    for i, freq in enumerate(freqs):
        start = int(i * note_duration * sr)
        end = int((i + 1) * note_duration * sr)
        note_t = np.linspace(0, note_duration, end - start, False)
        note = np.sin(2 * np.pi * freq * note_t)
        note += 0.3 * np.sin(2 * np.pi * freq * 2 * note_t)  # 2nd harmonic
        note = apply_adsr(note, 0.01, 0.05, 0.5, 0.05, sr)
        data[start:end] = note
    
    return normalize(data)

def generate_win(sr: int) -> np.ndarray:
    """Victory fanfare - major chord arpeggio."""
    duration = 0.6
    t = np.linspace(0, duration, int(sr * duration), False)
    
    # C major arpeggio: C4 -> E4 -> G4 -> C5 -> E5 -> G5
    freqs = [261.63, 329.63, 392.00, 523.25, 659.25, 783.99]
    note_duration = duration / len(freqs)
    
    data = np.zeros(int(sr * duration))
    for i, freq in enumerate(freqs):
        start = int(i * note_duration * sr)
        end = int((i + 1) * note_duration * sr)
        note_t = np.linspace(0, note_duration, end - start, False)
        note = np.sin(2 * np.pi * freq * note_t)
        note += 0.4 * np.sin(2 * np.pi * freq * 2 * note_t)  # 2nd harmonic
        note += 0.2 * np.sin(2 * np.pi * freq * 3 * note_t)  # 3rd harmonic
        note = apply_adsr(note, 0.01, 0.08, 0.6, 0.1, sr)
        data[start:end] = note
    
    return normalize(data)

def generate_lose(sr: int) -> np.ndarray:
    """Defeat sound - descending minor tones."""
    duration = 0.5
    t = np.linspace(0, duration, int(sr * duration), False)
    
    # Descending: A3 -> F3 -> D3
    freqs = [220.00, 174.61, 146.83]
    note_duration = duration / len(freqs)
    
    data = np.zeros(int(sr * duration))
    for i, freq in enumerate(freqs):
        start = int(i * note_duration * sr)
        end = int((i + 1) * note_duration * sr)
        note_t = np.linspace(0, note_duration, end - start, False)
        note = np.sin(2 * np.pi * freq * note_t)
        note += 0.3 * np.sin(2 * np.pi * freq * 2 * note_t)
        note = apply_adsr(note, 0.02, 0.1, 0.4, 0.15, sr)
        data[start:end] = note
    
    return normalize(data)

def generate_draw(sr: int) -> np.ndarray:
    """Draw sound - neutral two-tone."""
    duration = 0.35
    t = np.linspace(0, duration, int(sr * duration), False)
    
    # Two neutral tones: E4 -> E4
    freqs = [329.63, 329.63]
    note_duration = duration / len(freqs)
    
    data = np.zeros(int(sr * duration))
    for i, freq in enumerate(freqs):
        start = int(i * note_duration * sr)
        end = int((i + 1) * note_duration * sr)
        note_t = np.linspace(0, note_duration, end - start, False)
        note = np.sin(2 * np.pi * freq * note_t)
        note += 0.2 * np.sin(2 * np.pi * freq * 2 * note_t)
        note = apply_adsr(note, 0.01, 0.05, 0.5, 0.08, sr)
        data[start:end] = note
    
    return normalize(data)

def enhance_voice(input_path: str, output_path: str, sr: int) -> None:
    """Enhance existing voice file: high-pass filter + normalize + resample."""
    try:
        orig_sr, data = wavfile.read(input_path)
        
        # Convert to float
        if data.dtype == np.int16:
            data = data.astype(np.float32) / 32768.0
        elif data.dtype == np.int32:
            data = data.astype(np.float32) / 2147483648.0
        
        # High-pass filter to remove low-frequency noise (80 Hz cutoff)
        nyq = orig_sr / 2
        cutoff = 80 / nyq
        b, a = butter(4, cutoff, btype='high')
        data = filtfilt(b, a, data)
        
        # Resample to target sample rate
        if orig_sr != sr:
            # Use polyphase resampling for better quality
            gcd = np.gcd(orig_sr, sr)
            up = sr // gcd
            down = orig_sr // gcd
            data = resample_poly(data, up, down)
        
        # Normalize
        data = normalize(data)
        
        # Convert back to int16
        data = (data * 32767).astype(np.int16)
        
        # Write output
        wavfile.write(output_path, sr, data)
        print(f"Enhanced {input_path} -> {output_path} ({sr} Hz)")
    except Exception as e:
        print(f"Failed to enhance {input_path}: {e}")

def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    
    # Generate sound effects
    effects = {
        'snd_tap': generate_tap(SAMPLE_RATE),
        'snd_tick': generate_tick(SAMPLE_RATE),
        'snd_go': generate_go(SAMPLE_RATE),
        'snd_win': generate_win(SAMPLE_RATE),
        'snd_lose': generate_lose(SAMPLE_RATE),
        'snd_draw': generate_draw(SAMPLE_RATE),
    }
    
    for name, data in effects.items():
        path = f"{OUT_DIR}/{name}.wav"
        # Convert to int16
        pcm = (data * 32767).astype(np.int16)
        wavfile.write(path, SAMPLE_RATE, pcm)
        duration = len(pcm) / SAMPLE_RATE
        print(f"Generated {path}: {duration:.2f}s @ {SAMPLE_RATE} Hz")
    
    # Enhance existing voice files
    for event in ['win', 'lose', 'draw']:
        input_path = f"{OUT_DIR}/voice_{event}.wav"
        output_path = f"{OUT_DIR}/voice_{event}_enhanced.wav"
        enhance_voice(input_path, output_path, SAMPLE_RATE)
    
    print("\nAll audio files generated successfully!")

if __name__ == "__main__":
    main()
