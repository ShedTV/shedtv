# ShedTV mark, simplified from the hand-drawn logo. 100x100 box. Each shape: (points, fill).
NAVY, TAN, TAN_D, ROOF_A, ROOF_B, GREEN = "#14213D", "#E8B866", "#D49B45", "#9FB2A3", "#D3DDD2", "#2FB24C"
SW = 3.2  # outline width

def poly(pts): return "M" + " L".join(f"{x:.1f},{y:.1f}" for x, y in pts) + " Z"

CHIMNEY = [(20,12),(30,12),(30,16),(28.5,16),(28.5,32),(21.5,32),(21.5,16),(20,16)]
SIDE    = [(14,52),(42,52),(42,88),(14,86)]                 # side wall (darker)
FRONT   = [(42,52),(63,30),(84,52),(84,88),(42,88)]          # front wall with gable
ROOF    = [(40,20),(63,27),(38,55),(8,52)]                   # left roof plane
# roof stripes: bands across the plane from back edge to front edge
def lerp(a,b,t): return (a[0]+(b[0]-a[0])*t, a[1]+(b[1]-a[1])*t)
def band(t0,t1):
    # stripes run down the slope (ridge to eave), spaced along the ridge
    r0, r1 = lerp(ROOF[0],ROOF[1],t0), lerp(ROOF[0],ROOF[1],t1)
    e0, e1 = lerp(ROOF[3],ROOF[2],t0), lerp(ROOF[3],ROOF[2],t1)
    return [r0, r1, e1, e0]
STRIPES = [band(0.14,0.30), band(0.46,0.62), band(0.78,0.92)]
FASCIA  = [(63,23),(92,54),(88,58),(63,31),(38,59),(35,55)]  # front roof edge (navy)
DOOR    = [(50,59),(76,59),(76,88),(50,88)]
WINDOW  = [(20,60),(32,60),(32,72),(20,72)]
PLAY    = [(56.5,63.5),(56.5,83.5),(73,73.5)]

def svg(extra_bg=None, scale=1.0, dx=0, dy=0, size=100, outline=NAVY):
    g = []
    def p(pts, fill, stroke=True, w=SW):
        s = f' stroke="{outline}" stroke-width="{w}" stroke-linejoin="round"' if stroke else ""
        g.append(f'<path d="{poly(pts)}" fill="{fill}"{s}/>')
    p(CHIMNEY, TAN)
    p(SIDE, TAN_D)
    p(FRONT, TAN)
    p(ROOF, ROOF_A)
    for st in STRIPES: p(st, ROOF_B, stroke=False)
    p(ROOF, "none")
    p(FASCIA, outline, stroke=False)
    p(DOOR, TAN)
    g.append(f'<path d="M58.7,60.5 V86.5 M67.3,60.5 V86.5" stroke="{outline}" stroke-width="1.6"/>')
    p(WINDOW, outline)
    p(PLAY, GREEN, w=2.6)
    bg = f'<rect width="{size}" height="{size}" fill="{extra_bg}"/>' if extra_bg else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {size} {size}">{bg}'
            f'<g transform="translate({dx},{dy}) scale({scale})">' + "".join(g) + "</g></svg>")
