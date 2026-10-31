"""Genera los sonidos modernos del teclado (originales, hechos con matemáticas).

Tres sonidos como en los teclados de hoy: letra (tick agudo y seco), borrar (un poco más grave)
y espacio/enter (un "toc" más grave y suave). Uso: python3 tools/make_sounds.py
"""
import wave
import numpy as np

SR = 44100
rng = np.random.default_rng(7)


def band_noise(n, lo, hi):
    x = rng.standard_normal(n)
    f = np.fft.rfft(x)
    freqs = np.fft.rfftfreq(n, 1 / SR)
    f[(freqs < lo) | (freqs > hi)] = 0
    y = np.fft.irfft(f, n)
    return y / (np.abs(y).max() + 1e-9)


def click(ms, noise_band, noise_tau, body_hz, body_tau, thump_hz, thump_tau, mix):
    n = int(SR * ms / 1000)
    t = np.arange(n) / SR
    attack = np.minimum(1, t / 0.0004)  # 0.4 ms de subida: sin chasquido digital
    noise = band_noise(n, *noise_band) * np.exp(-t / noise_tau)
    body = np.sin(2 * np.pi * body_hz * t) * np.exp(-t / body_tau)
    thump = np.sin(2 * np.pi * thump_hz * t) * np.exp(-t / thump_tau)
    y = attack * (mix[0] * noise + mix[1] * body + mix[2] * thump)
    y *= np.minimum(1, (n - np.arange(n)) / (SR * 0.004))  # final suave
    return y / np.abs(y).max() * 0.85


def save(name, y):
    data = (y * 32767).astype(np.int16)
    with wave.open(f"app/src/main/res/raw/{name}.wav", "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data.tobytes())
    print(name, len(data), "muestras")


save("click_key", click(38, (2200, 7000), 0.0028, 1750, 0.0045, 260, 0.004, (0.75, 0.35, 0.25)))
save("click_delete", click(42, (1600, 5200), 0.0034, 1250, 0.0055, 210, 0.005, (0.7, 0.4, 0.3)))
save("click_space", click(55, (900, 3200), 0.0045, 620, 0.008, 150, 0.007, (0.55, 0.45, 0.45)))
