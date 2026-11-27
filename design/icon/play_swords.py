"""Builds the Play tab icon (RiftIcons.CrossedSwords): two crossed swords as one even-odd path.
Run: pip install shapely; python3 design/icon/play_swords.py  (prints the path data)."""
import math
from shapely.geometry import Polygon, Point
from shapely.ops import unary_union
PIV = (12, 8.6)  # where the blades cross: high, so the guards stay apart
A = math.radians(45)
def raw(pts, a):
    c, s = math.cos(a), math.sin(a); px, py = PIV
    return [(px + (x - px) * c - (y - py) * s, py + (x - px) * s + (y - py) * c) for x, y in pts]
def sword(a, bw=1.1, gw=2.7):
    blade = Polygon(raw([(12, 1.0), (12 + bw, 2.9), (12 + bw, 15.2), (12 - bw, 15.2), (12 - bw, 2.9)], a))
    guard = Polygon(raw([(12 - gw, 15.2), (12 + gw, 15.2), (12 + gw - .35, 16.5), (12 - gw + .35, 16.5)], a))
    grip = Polygon(raw([(11.4, 16.5), (12.6, 16.5), (12.6, 19.4), (11.4, 19.4)], a))
    (px, py), = raw([(12, 20.55)], a)
    return unary_union([blade, guard, grip, Point(px, py).buffer(1.15, resolution=24)])
back, front = sword(-A), sword(A)
shape = unary_union([back.difference(front.buffer(0.51, join_style=2)), front])
x0, y0, x1, y1 = shape.bounds; k = 21.6 / max(x1 - x0, y1 - y0); ox = 12 - (x0 + x1) / 2 * k; oy = 12 - (y0 + y1) / 2 * k
ring = lambda cs: "M" + "L".join(f"{ox + x * k:.2f} {oy + y * k:.2f}" for x, y in list(cs)[:-1]) + "Z"
geoms = list(shape.geoms) if shape.geom_type == "MultiPolygon" else [shape]
print("".join(ring(g.exterior.coords) + "".join(ring(i.coords) for i in g.interiors) for g in geoms))
