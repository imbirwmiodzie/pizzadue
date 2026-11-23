"""Cuts each pizza out of the menu photos for the guests' page.

Each photo (menu-1.jpg ... menu-3.jpg) holds two rows of pizzas on white plates. Every pizza is
cut out round, without its plate, on a transparent background, and saved as
server/src/main/resources/party/pizza-<name>.webp (560 x 560), the name the page's pizza.js uses.

    pip install opencv-python-headless numpy
    python3 scripts/pizza_photos/cut.py
"""
import os

import cv2
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "server", "src", "main", "resources", "party")
SIZE = 560

# the pizzas on each photo, row by row, left to right
NAMES = {
    "menu-1.jpg": ["diavola", "capricciosa", "salsiccia-funghi", "quattro-formaggi"],
    "menu-2.jpg": ["tonno-cipolla", "nutella", "tonno", "hawaii"],
    "menu-3.jpg": ["salsiccia", "guanciale-cipolla", "marinara", "rucola-grana", "margherita"],
}


def outlines(img):
    """Each pizza's outline, in reading order."""
    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
    # background, plates and their shadows are grey to white: hardly any colour
    food = (hsv[..., 1] > 34).astype(np.uint8) * 255
    # cut between the two rows, so no two pizzas touch
    mid = img.shape[0] // 2
    food[mid - 6:mid + 8] = 0
    food = cv2.morphologyEx(food, cv2.MORPH_OPEN, np.ones((5, 5), np.uint8))
    food = cv2.morphologyEx(food, cv2.MORPH_CLOSE, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (25, 25)))
    n, labels, stats, _ = cv2.connectedComponentsWithStats(food)
    found = []
    for i in range(1, n):
        x, y, _, _, area = stats[i]
        if area < 40000:
            continue
        cnts, _ = cv2.findContours((labels == i).astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
        found.append((y // mid, x, cv2.convexHull(max(cnts, key=cv2.contourArea))))
    found.sort(key=lambda f: (f[0], f[1]))
    return [f[2] for f in found]


def cut(img, hull):
    """The pizza as a square RGBA image, centred, transparent around it."""
    mask = np.zeros(img.shape[:2], np.uint8)
    cv2.fillPoly(mask, [hull], 255)
    # the pizza is round: nothing beyond its usual radius (no spikes where it touched something)
    m = cv2.moments(mask, binaryImage=True)
    cx, cy = m["m10"] / m["m00"], m["m01"] / m["m00"]
    edge = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)[0][0].reshape(-1, 2)
    r = np.median(np.hypot(edge[:, 0] - cx, edge[:, 1] - cy))
    disc = np.zeros_like(mask)
    cv2.circle(disc, (int(round(cx)), int(round(cy))), int(r * 1.025), 255, -1)
    mask = cv2.bitwise_and(mask, disc)
    # a hair inside the edge, so no plate shows, then a soft edge
    mask = cv2.erode(mask, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5)))
    mask = cv2.GaussianBlur(mask, (5, 5), 0)
    (cx, cy), r = cv2.minEnclosingCircle(cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)[0][0])
    r, cx, cy = int(r) + 4, int(cx), int(cy)
    pad = r + 2
    big = cv2.copyMakeBorder(img, pad, pad, pad, pad, cv2.BORDER_CONSTANT, value=(0, 0, 0))
    alpha = cv2.copyMakeBorder(mask, pad, pad, pad, pad, cv2.BORDER_CONSTANT, value=0)
    x0, y0 = cx + pad - r, cy + pad - r
    rgba = np.dstack([big[y0:y0 + 2 * r, x0:x0 + 2 * r], alpha[y0:y0 + 2 * r, x0:x0 + 2 * r]])
    return cv2.resize(rgba, (SIZE, SIZE), interpolation=cv2.INTER_AREA)


if __name__ == "__main__":
    for photo, names in NAMES.items():
        img = cv2.imread(os.path.join(HERE, photo))
        found = outlines(img)
        if len(found) != len(names):
            raise SystemExit(f"{photo}: found {len(found)} pizzas, expected {len(names)}")
        for name, hull in zip(names, found):
            path = os.path.join(OUT, f"pizza-{name}.webp")
            cv2.imwrite(path, cut(img, hull), [cv2.IMWRITE_WEBP_QUALITY, 82])
            print(f"{path} ({os.path.getsize(path) // 1024} KB)")
