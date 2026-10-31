"""Genera los sonidos del teclado (originales, hechos con matemáticas).

Estilo: toque digital de pantalla, corto, suave y seco ("pop" de membrana híbrida),
sin eco, menos de 20 milisegundos. Tres variantes: letra, borrar y espacio/enter.
Uso: python3 tools/make_sounds.py
"""
import wave
import numpy as np

SR = 44100
rng = np.random.default_rng(3)


def lowpass(x, hz):
    f = np.fft.rfft(x)
    freqs = np.fft.rfftfreq(len(x), 1 / SR)
    f *= 1 / (1 + (freqs / hz) ** 4)  # corte suave
    return np.fft.irfft(f, len(x))


def pop(ms, f0, f1, tau, click_amt, peak):
    n = int(SR * ms / 1000)
    t = np.arange(n) / SR
    # Tono que baja rápido de f0 a f1: suena a "pop" (como una membrana que se hunde)
    freq = f1 + (f0 - f1) * np.exp(-t / 0.0018)
    phase = 2 * np.pi * np.cumsum(freq) / SR
    tone = np.sin(phase) * np.exp(-t / tau)
    # Clic de contacto, muy cortito y filtrado (seco, sin brillo metálico)
    click = lowpass(rng.standard_normal(n), 3800) * np.exp(-t / 0.0006)
    y = tone + click_amt * click
    attack = np.minimum(1, t / 0.0003)
    release = np.clip((n - np.arange(n)) / (SR * 0.003), 0, 1)  # termina en silencio total
    y = y * attack * release
    return y / np.abs(y).max() * peak


def save(name, y):
    data = (y * 32767).astype(np.int16)
    with wave.open(f"app/src/main/res/raw/{name}.wav", "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data.tobytes())
    print(name, f"{len(data) / SR * 1000:.1f} ms")


save("click_key", pop(16, 1400, 700, 0.0028, 0.35, 0.55))
save("click_delete", pop(17, 1100, 560, 0.0031, 0.3, 0.55))
save("click_space", pop(19, 820, 400, 0.0036, 0.25, 0.6))
