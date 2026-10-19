#!/usr/bin/env python3
"""Generate synthetic viewer goldens with an independent closed-form RAS inverse.

Standard library only. No Android code, patient files, or conversion code is used.
Run: python scripts/qa/viewer_reference.py
"""
from pathlib import Path
import math
import struct

OUT = Path(__file__).resolve().parents[2] / "app/src/test/resources/viewer-reference"
SHAPE = (7, 6, 5)
BOUNDS = ((4.5, -8.0, 20.0), (12.8, -0.8, 28.5))


def ijk(r, a, s):
    # R=12-1.5*j+0.2*k, A=-8+1.2*i, S=20+0.1*j+2*k.
    # Solve the two linear equations analytically, independently of SpatialAffine.
    return ((a + 8) / 1.2, (2 * (r - 12) - .2 * (s - 20)) / -3.02,
            (-.1 * (r - 12) - 1.5 * (s - 20)) / -3.02)


def intensity(world):
    i, j, k = ijk(*world)
    if not (-.0001 <= i <= 6.0001 and -.0001 <= j <= 5.0001 and -.0001 <= k <= 4.0001):
        return 0
    # A linear field is its own exact trilinear interpolant.
    value = min(6, max(0, i)) * 17 + min(5, max(0, j)) * 3.5 + min(4, max(0, k)) * .75 - 50
    return max(0, min(255, math.floor((value + 50) / 122.5 * 255 + .5)))


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    header = bytearray(352)
    struct.pack_into("<i", header, 0, 348)
    struct.pack_into("<8h", header, 40, 4, *SHAPE, 2, 1, 1, 1)
    struct.pack_into("<2h", header, 70, 16, 32)
    struct.pack_into("<8f", header, 76, 1, 1.2, math.sqrt(2.26), math.sqrt(4.04), 1, 1, 1, 1)
    struct.pack_into("<f", header, 108, 352)
    header[123] = 2  # millimetres
    struct.pack_into("<h", header, 254, 1)
    struct.pack_into("<12f", header, 280, 0, -1.5, .2, 12, 1.2, 0, 0, -8, 0, .1, 2, 20)
    header[344:348] = b"n+1\0"
    values = [i * 17 + j * 3.5 + k * .75 - 50 for k in range(5) for j in range(6) for i in range(7)]
    # Deliberately very different second frame exposes accidental timepoint mixing.
    (OUT / "asymmetric-oblique-4d.nii").write_bytes(header + struct.pack("<420f", *values, *[v + 10000 for v in values]))
    rows = []
    for name, fixed, horizontal, vertical in (("AXIAL", 2, 0, 1), ("CORONAL", 1, 0, 2), ("SAGITTAL", 0, 1, 2)):
        span = [BOUNDS[1][a] - BOUNDS[0][a] for a in range(3)]
        count = math.floor(span[fixed] / 1.2 + .5) + 1
        selected = count // 2
        position = BOUNDS[0][fixed] + selected / (count - 1) * span[fixed]
        aspect = span[horizontal] / span[vertical]
        width = 96 if aspect >= 1 else math.floor(96 * aspect + .5)
        height = 96 if aspect < 1 else math.floor(96 / aspect + .5)
        pixels = []
        for y in range(height):
            for x in range(width):
                world = [0., 0., 0.]
                world[fixed] = position
                world[horizontal] = BOUNDS[1][horizontal] - x / (width - 1) * span[horizontal]
                world[vertical] = BOUNDS[1][vertical] - y / (height - 1) * span[vertical]
                pixels.append(intensity(world))
        (OUT / f"{name.lower()}.pgm").write_text(f"P2\n{width} {height}\n255\n" + " ".join(map(str, pixels)) + "\n", encoding="ascii")
        rows.append(f"{name},{selected},{count},{position:.12f}")
    (OUT / "planes.csv").write_text("\n".join(rows) + "\n", encoding="ascii")
    print(f"Generated independent synthetic NIfTI and 3 grayscale reference planes in {OUT}")


if __name__ == "__main__":
    main()
