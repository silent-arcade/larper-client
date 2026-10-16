// Generates the knife item models and texture.
//   node tools/gen-knife-models.js            -> writes assets
//   node tools/gen-knife-models.js <outDir>   -> also writes front-view preview PNGs to outDir
// Knives are designed upright (handle at the bottom, tip at the top) and tilted -45° about (8, 8)
// so they sit in the hand like vanilla swords. Keep TILT/pivots in sync with KnifeType.java.
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const RES = path.join(__dirname, '..', 'src/main/resources/assets/larper-client');
const TILT = -45;

// 4x4 grid of 4x4px swatches in a 16x16 texture
const PALETTE = [
  ['#C3CBD3', '#E6EBF0', '#F7FAFC', '#7E8790'], // steel, steelLight, edge, steelDark
  ['#59616B', '#17181B', '#26282D', '#3E4249'], // fuller, black, rubber, grey
  ['#6E4528', '#4A2C18', '#B3262E', '#C9A23F'], // wood, woodDark, red, gold
  ['#9AA3AD', '#2F5DA8', '#EDEDED', '#0B0B0D'], // pin, blue, white, darkest
];
const NAMES = [
  ['steel', 'steelLight', 'edge', 'steelDark'],
  ['fuller', 'black', 'rubber', 'grey'],
  ['wood', 'woodDark', 'red', 'gold'],
  ['pin', 'blue', 'white', 'darkest'],
];
const SW = {};
NAMES.forEach((row, r) => row.forEach((n, c) => (SW[n] = [c, r])));

const rad = d => (d * Math.PI) / 180;
const deg = r => (r * 180) / Math.PI;
const round = v => Math.round(v * 1000) / 1000;

// Upright point -> model space after the tilt
function tilt([x, y]) {
  const dx = x - 8, dy = y - 8, c = Math.cos(rad(TILT)), s = Math.sin(rad(TILT));
  return [8 + dx * c - dy * s, 8 + dx * s + dy * c];
}

// Box (model space) rotated by angle (deg, around z) about origin.
// sw: a swatch name from the palette, or { tex, uv } to sample another texture (blade finishes).
function el([x1, y1, z1, x2, y2, z2], sw, origin, angle) {
  let uv, tex = '#k';
  if (typeof sw === 'string') {
    const [c, r] = SW[sw];
    uv = [c * 4 + 1, r * 4 + 1, c * 4 + 3, r * 4 + 3];
  } else {
    ({ uv, tex } = sw);
  }
  const faces = {};
  for (const f of ['north', 'east', 'south', 'west', 'up', 'down']) faces[f] = { uv, texture: tex };
  const e = { from: [x1, y1, z1].map(round), to: [x2, y2, z2].map(round), faces };
  if (round(angle) !== 0) e.rotation = { angle: round(angle), axis: 'z', origin: origin.map(round) };
  return e;
}

// Axis-aligned box in the upright frame
const box = (b, sw) => el(b, sw, [8, 8, 8], TILT);

// Strip along an upright polyline. wl/wr: width to the left/right of the travel direction.
function sweep(pts, wl, wr, z1, z2, sw, overlap = 0.06) {
  const out = [];
  for (let i = 0; i < pts.length - 1; i++) {
    const p = pts[i], q = pts[i + 1];
    const dx = q[0] - p[0], dy = q[1] - p[1], len = Math.hypot(dx, dy);
    if (len < 1e-4) continue;
    const a = deg(Math.atan2(-dx, dy)); // angle from "up", counter-clockwise
    const L = typeof wl === 'function' ? wl(i / (pts.length - 1), i) : wl;
    const R = typeof wr === 'function' ? wr(i / (pts.length - 1), i) : wr;
    const S = typeof sw === 'function' ? sw(i / (pts.length - 1), i) : sw;
    const P = tilt(p);
    out.push(el([P[0] - L, P[1] - overlap, z1, P[0] + R, P[1] + len + overlap, z2], S, [P[0], P[1], 8], TILT + a));
  }
  return out;
}

// Rounded bar along an upright polyline: three stacked layers (wide and thin, mid, narrow and thick) so the
// cross-section reads as an oval instead of a box. w: half-width (number or (t, i) => number), th: half-thickness.
function rsweep(pts, w, th, sw, zc = 8, overlap = 0.06) {
  const W = typeof w === 'function' ? w : () => w;
  return [[1, 0.5], [0.88, 0.82], [0.62, 1]].flatMap(([kw, kt]) =>
    sweep(pts, (t, i) => W(t, i) * kw, (t, i) => W(t, i) * kw, zc - th * kt, zc + th * kt, sw, overlap));
}

// Offset a polyline sideways (positive = left of travel direction)
function offset(pts, d) {
  return pts.map((p, i) => {
    const a = pts[Math.max(0, i - 1)], b = pts[Math.min(pts.length - 1, i + 1)];
    const dx = b[0] - a[0], dy = b[1] - a[1], len = Math.hypot(dx, dy) || 1;
    const k = typeof d === 'function' ? d(i / (pts.length - 1)) : d;
    return [p[0] - (dy / len) * k, p[1] + (dx / len) * k];
  });
}

const bezier = (p0, p1, p2, n) => Array.from({ length: n + 1 }, (_, i) => {
  const t = i / n, u = 1 - t;
  return [u * u * p0[0] + 2 * u * t * p1[0] + t * t * p2[0], u * u * p0[1] + 2 * u * t * p1[1] + t * t * p2[1]];
});

const circle = (cx, cy, r, n) => Array.from({ length: n + 1 }, (_, i) => {
  const a = (i / n) * 2 * Math.PI;
  return [cx + Math.cos(a) * r, cy + Math.sin(a) * r];
});

const line = (a, b, n) => Array.from({ length: n + 1 }, (_, i) => [a[0] + ((b[0] - a[0]) * i) / n, a[1] + ((b[1] - a[1]) * i) / n]);

// A single-edged blade from a spine polyline and an edge polyline (both base -> tip, same length)
// Every visible line (spine, grind line, edge) is the straight side of strips anchored on that line, so all of
// them stay smooth. Each region gets one strip from each of its two borders; they overlap in the middle, where
// the stepped inner ends of one are hidden by the other.
//   flat (steel, full thickness): spine <-> grind line,  bevel (lighter, thinner): grind line <-> edge
function blade(spine, edge, { flatSw = 'steel', spineSw = 'steelLight', edgeSw = 'edge', zc = [7.72, 8.28], grind = 0.6, finish = null } = {}) {
  const out = [];
  const n = spine.length - 1;
  if (finish) {
    // Finish colors run along the blade: sample the finish texture at each segment's position (0 base -> 1 tip)
    flatSw = (t, i) => finishSw(finish, 0, (i + 0.5) / n);
    edgeSw = (t, i) => finishSw(finish, 1, (i + 0.5) / n);
    spineSw = (t, i) => finishSw(finish, 2, (i + 0.5) / n);
  }
  const spineLeft = cross(spine, edge);
  const seam = spine.map((s, i) => [s[0] + (edge[i][0] - s[0]) * grind, s[1] + (edge[i][1] - s[1]) * grind]);
  const width = i => Math.hypot(edge[i][0] - spine[i][0], edge[i][1] - spine[i][1]);
  // Narrower end of each segment, so nothing pokes past the opposite border near the tip
  const segW = (k, max = Infinity) => (t, i) => Math.max(0.01, Math.min(max, Math.min(width(i), width(i + 1)) * k));
  // towardEdge: strip grows from its anchor line toward the edge side (else toward the spine side)
  const strip = (pts, w, z1, z2, sw, towardEdge) =>
    sweep(pts, towardEdge === spineLeft ? 0 : w, towardEdge === spineLeft ? w : 0, z1, z2, sw, 0.04);
  const flat = grind * 0.62, bevel = (1 - grind) * 0.62;
  out.push(...strip(spine, segW(flat), zc[0], zc[1], flatSw, true));
  out.push(...strip(seam, segW(flat), zc[0] + 0.01, zc[1] - 0.01, flatSw, false));
  out.push(...strip(edge, segW(bevel), zc[0] + 0.07, zc[1] - 0.07, edgeSw, false));
  out.push(...strip(seam, segW(bevel), zc[0] + 0.08, zc[1] - 0.08, edgeSw, true));
  out.push(...strip(spine, segW(0.16, 0.16), zc[0] - 0.02, zc[1] + 0.02, spineSw, true)); // spine highlight
  // Solid fill for the last few segments, where the strips get too thin to overlap
  const k = Math.max(0, spine.length - 6);
  const mid = spine.slice(k).map((s, i) => [(s[0] + edge[k + i][0]) / 2, (s[1] + edge[k + i][1]) / 2]);
  const half = (t, i) => Math.max(0.01, Math.min(width(k + i), width(k + i + 1)) * 0.5);
  const tipSw = typeof edgeSw === 'function' ? (t, i) => edgeSw(t, k + i) : edgeSw;
  out.push(...sweep(mid, half, half, zc[0] + 0.09, zc[1] - 0.09, tipSw, 0.04));
  return out;
}

// ---------- Finishes (CS2-style blade skins) ----------
// Each is a color along the blade, t = 0 at the base, 1 at the tip.
const hexRgb = h => [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16));
const lerp = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);
const grad = stops => t => {
  for (let i = 0; i < stops.length - 1; i++) {
    const [t0, c0] = stops[i], [t1, c1] = stops[i + 1];
    if (t <= t1) return lerp(hexRgb(c0), hexRgb(c1), Math.max(0, (t - t0) / (t1 - t0)));
  }
  return hexRgb(stops[stops.length - 1][1]);
};
const FINISHES = {
  fade: grad([[0, '#5B3FD6'], [0.4, '#D9469E'], [0.72, '#F59A6A'], [1, '#F8E27A']]),
  marble_fade: grad([[0, '#2E6FE0'], [0.22, '#2E6FE0'], [0.42, '#D8322E'], [0.62, '#F2C230'], [0.8, '#D8322E'], [1, '#2E6FE0']]),
  tiger_tooth: t => lerp(hexRgb('#F4C24E'), hexRgb('#B9661A'), Math.pow(0.5 + 0.5 * Math.sin(t * 46), 3)),
  ruby: grad([[0, '#6E0A18'], [0.5, '#C4142F'], [1, '#F0405A']]),
  sapphire: grad([[0, '#0E2470'], [0.5, '#1F55D8'], [1, '#53A0FF']]),
  emerald: grad([[0, '#064D2A'], [0.5, '#139C55'], [1, '#4BE38E']]),
  night: grad([[0, '#24272D'], [0.5, '#3A3E46'], [1, '#2C3036']]),
};
// Texture rows: 0 flat, 1 edge bevel (lighter), 2 spine highlight (lightest), 3 fuller (darker)
const FINISH_ROWS = [c => c, c => lerp(c, [255, 255, 255], 0.22), c => lerp(c, [255, 255, 255], 0.4), c => lerp(c, [0, 0, 0], 0.45)];
const FINISH_W = 64;
function finishSw(finish, row, t) {
  const u = ((Math.min(FINISH_W - 1, Math.floor(t * FINISH_W)) + 0.5) / FINISH_W) * 16;
  return { tex: '#f', uv: [round(u - 0.02), row * 4 + 1, round(u + 0.02), row * 4 + 3] };
}
function finishColor(finish, row, t) {
  return FINISH_ROWS[row](FINISHES[finish](t)).map(v => Math.round(Math.max(0, Math.min(255, v))));
}

// True if the edge line lies to the right of the spine (when walking base -> tip)
function cross(spine, edge) {
  const a = spine[0], b = spine[Math.min(2, spine.length - 1)], e = edge[0];
  return (b[0] - a[0]) * (e[1] - a[1]) - (b[1] - a[1]) * (e[0] - a[0]) < 0;
}

// ---------- Bayonet ----------
function bayonet(finish) {
  const parts = [];
  // Pommel: rounded steel cap with the lanyard hole
  parts.push(...rsweep(line([8, -0.35], [8, 1.25], 4), t => 0.78 + 0.12 * Math.sin(Math.PI * t), 0.82, 'steel'));
  parts.push(box([7.65, 0.2, 7.0, 8.35, 0.8, 9.0], 'darkest')); // lanyard hole
  // Grip: swells in the middle like the real M9 handle, with flush groove rings
  const grip = line([8, 1.2], [8, 6.65], 22);
  const gw = t => 0.8 + 0.1 * Math.sin(Math.PI * t) - 0.04 * t;
  parts.push(...rsweep(grip, gw, 0.78, 'rubber'));
  for (let i = 2; i < 21; i += 3) parts.push(...rsweep(grip.slice(i, i + 2), (t, k) => gw((i + k) / 22) + 0.015, 0.795, 'black', 8, 0));
  // Guard: rounded crossbar with the muzzle ring on the spine side
  parts.push(...rsweep(line([6.0, 6.95], [10.05, 6.95], 10), t => 0.36 + 0.06 * Math.sin(Math.PI * t), 0.72, 'steelDark'));
  parts.push(...sweep(circle(5.55, 6.95, 0.62, 18), 0.15, 0.15, 7.45, 8.55, 'steelDark')); // muzzle ring
  parts.push(box([7.05, 7.3, 7.7, 9.25, 7.9, 8.3], 'steel')); // ricasso

  const n = 40, y0 = 7.85, yt = 16.4;
  const ys = Array.from({ length: n + 1 }, (_, i) => y0 + ((yt - y0) * i) / n);
  const spineX = y => (y < 13.6 ? 7.05 : 7.05 + Math.pow((y - 13.6) / (yt - 13.6), 1.6) * 1.1); // clip point
  const edgeX = y => (y < 12.2 ? 9.25 : 9.25 - Math.pow((y - 12.2) / (yt - 12.2), 1.8) * 1.1); // belly
  const spine = ys.map(y => [spineX(y), y]);
  const edge = ys.map(y => [edgeX(y), y]);
  parts.push(...blade(spine, edge, { finish }));
  parts.push(box([7.5, 8.2, 7.69, 7.85, 13.0, 8.31], finish ? finishSw(finish, 3, 0.35) : 'fuller'));
  return parts;
}

// ---------- Karambit (inventory icon / third person; first person uses the CS:GO viewmodel) ----------
function karambit(finish) {
  const parts = [];
  parts.push(...rsweep(circle(8.4, 0.35, 1.25, 28), 0.3, 0.62, 'steel', 8, 0.08)); // finger ring
  const handle = bezier([8.3, 1.5], [7.95, 4.4], [7.55, 7.3], 16);
  const hw = t => 0.86 + 0.1 * Math.sin(Math.PI * t);
  parts.push(...rsweep(handle, hw, 0.72, 'black'));
  for (let i = 2; i < handle.length - 2; i += 3) parts.push(...rsweep(handle.slice(i, i + 2), (t, k) => hw((i + k) / 16) + 0.015, 0.735, 'rubber', 8, 0));
  parts.push(...rsweep(bezier([7.56, 7.1], [7.53, 7.45], [7.5, 7.8], 3), 1.02, 0.66, 'steelDark')); // bolster
  const c = bezier([7.5, 7.7], [7.2, 12.9], [11.6, 14.9], 36);
  const w = t => 1.0 * Math.pow(1 - t, 0.85) + 0.05;
  const spine = offset(c, w);              // outer (convex) side, left of travel
  const edge = offset(c, t => -w(t) * 0.95); // inner (concave) side is sharpened
  parts.push(...blade(spine, edge, { grind: 0.55, finish }));
  return parts;
}

// ---------- Butterfly (three parts so the handles can swing) ----------
const BF = { pinA: [7.5, 7.2], pinB: [8.5, 7.2] };
// Pins run through the tang by design; the collision check ignores pin <-> tang overlaps (and only those)
const PIN = { set: new WeakSet(), tag(e) { this.set.add(e); return e; } };
const TANG = { set: new WeakSet(), tag(e) { this.set.add(e); return e; } };

function butterflyBlade(finish) {
  const parts = [];
  parts.push(TANG.tag(box([7.2, 6.7, 7.74, 8.8, 7.75, 8.26], 'steelDark'))); // tang
  const n = 32, y0 = 7.7, yt = 14.3;
  const ys = Array.from({ length: n + 1 }, (_, i) => y0 + ((yt - y0) * i) / n);
  const spine = ys.map(y => [y < 12.6 ? 7.45 : 7.45 + Math.pow((y - 12.6) / (yt - 12.6), 1.5) * 0.6, y]);
  const edge = ys.map(y => [y < 11.6 ? 8.55 : 8.55 - Math.pow((y - 11.6) / (yt - 11.6), 1.7) * 0.5, y]);
  parts.push(...blade(spine, edge, { finish }));
  return parts;
}

function butterflyHandle(side) {
  // side -1 = handle A (safe, left), +1 = handle B (bite, right); x range is [x0, x1].
  // Each handle is a channel: two side plates (z outside the blade) and a solid back (full z). The backs sit on the
  // inner sides (x = 8) so the channels open toward where the blade swings in; the blade only ever moves between the
  // plates, so nothing passes through anything. Keep everything below y = 6.6 clear of the tang, and the end caps
  // below y = 0.1, where the closed blade's tip ends up.
  // The handles turn about pins 1px apart, so above the pins each handle stays inside a round head of radius
  // < 0.5 around its pin (like the rounded heads of a real balisong); the collision check below proves it.
  const [x0, x1] = side < 0 ? [7.0, 8.0] : [8.0, 9.0];
  const back = side < 0 ? [x1 - 0.25, x1] : [x0, x0 + 0.25];
  const pin = side < 0 ? BF.pinA : BF.pinB;
  const parts = [];
  parts.push(box([back[0], 0.05, 7.25, back[1], 6.6, 8.75], 'steelDark'));
  for (const [z1, z2] of [[7.25, 7.5], [8.5, 8.75]]) {
    for (const [ya, yb] of [[-0.15, 0.9], [1.6, 1.9], [2.6, 2.9], [3.6, 3.9], [4.6, 4.9], [5.6, pin[1]]]) parts.push(box([x0, ya, z1, x1, yb, z2], 'steel'));
    // Round head: a square and a 45°-turned square (corners 0.49 from the pin) make an octagon
    parts.push(box([pin[0] - 0.35, pin[1] - 0.35, z1, pin[0] + 0.35, pin[1] + 0.35, z2], 'steel'));
    const P = tilt(pin);
    parts.push(el([P[0] - 0.35, P[1] - 0.35, z1, P[0] + 0.35, P[1] + 0.35, z2], 'steel', [P[0], P[1], 8], TILT + 45));
  }
  parts.push(box([x0, -0.15, 7.25, x1, 0.05, 8.75], 'steelDark')); // end cap
  parts.push(PIN.tag(box([pin[0] - 0.18, pin[1] - 0.18, 7.1, pin[0] + 0.18, pin[1] + 0.18, 8.9], 'pin'))); // pin through the tang
  if (side > 0) parts.push(box([8.15, -0.75, 7.6, 8.6, -0.15, 8.4], 'gold')); // latch
  return parts;
}

// ---------- Halo (Cinematic Death angels) ----------
// A flat golden ring in the horizontal plane: short tangent bars turned around the centre
function halo() {
  const parts = [], n = 22, r = 4.6, len = (2 * Math.PI * r) / n + 0.25;
  for (let i = 0; i < n; i++) {
    const e = el([8 - len / 2, 7.7, 8 - r - 0.5, 8 + len / 2, 8.3, 8 - r + 0.5], 'gold', [8, 8, 8], 0);
    e.rotation = { angle: round((360 * i) / n), axis: 'y', origin: [8, 8, 8] };
    parts.push(e);
  }
  return parts;
}

// ---------- Angel wing (Cinematic Death angels) ----------
// A feathered wing in the XY plane, root at (1, 14) (the shoulder), spanning out to the right. Long flight feathers
// hang down from the arm bone, fanning outward and getting longer toward the tip; shorter covert feathers lie on top.
const WING_ROOT = [1, 14];
function angelWing() {
  const parts = [];
  // The arm bone arcs up and out from the shoulder, then sweeps back down toward the tip
  const bone = t => [WING_ROOT[0] + 15 * t, WING_ROOT[1] + 3.2 * Math.sin(Math.PI * Math.min(1, t * 1.15))];
  // One feather: a long strip that narrows to a rounded tip (stacked strips of falling width), hanging from the bone
  const feather = (t, len, width, z, sw, tipSw, fan) => {
    const [x, y] = bone(t);
    const rot = { angle: round(Math.max(-45, Math.min(45, 6 + fan * t))), axis: 'z', origin: [round(x), round(y), 8] };
    for (const [k, l, s] of [[1, 0.78, sw], [0.7, 0.92, sw], [0.38, 1, tipSw]]) {
      parts.push({ ...el([x - (width * k) / 2, y - len * l, z, x + (width * k) / 2, y + 0.3, z + 0.22], s, [x, y, 8], 0), rotation: rot });
    }
  };
  const n = 17;
  for (let i = 0; i < n; i++) { // long flight feathers, gold tipped
    const t = i / (n - 1);
    feather(t, 7 + 11 * Math.pow(t, 1.2), 1.2, 7.62 + 0.004 * i, i % 2 ? 'edge' : 'white', 'gold', 42);
  }
  for (let i = 0; i < n; i++) { // secondaries
    const t = i / (n - 1);
    feather(t, 4.5 + 5.5 * t, 1.12, 7.86 + 0.004 * i, i % 2 ? 'white' : 'edge', 'steelLight', 36);
  }
  for (let i = 0; i < n; i++) { // soft coverts on top
    const t = i / (n - 1);
    feather(t, 2.4 + 2.6 * t, 1.05, 8.1 + 0.004 * i, 'edge', 'white', 30);
  }
  for (let i = 0; i < 8; i++) { // the arm bone
    const [x0, y0] = bone(i / 8), [x1, y1] = bone((i + 1) / 8);
    const a = deg(Math.atan2(y1 - y0, x1 - x0)), len = Math.hypot(x1 - x0, y1 - y0) + 0.25;
    parts.push({ ...el([x0, y0 - 0.55, 7.6, x0 + len, y0 + 0.55, 8.5], 'gold', [x0, y0, 8], 0), rotation: { angle: round(Math.max(-45, Math.min(45, a))), axis: 'z', origin: [round(x0), round(y0), 8] } });
  }
  return parts;
}

// Display transforms of vanilla handheld items (swords). Keep in sync with KnifeType.java.
const display = {
  thirdperson_righthand: { rotation: [0, -90, 55], translation: [0, 4, 0.5], scale: [0.85, 0.85, 0.85] },
  thirdperson_lefthand: { rotation: [0, 90, -55], translation: [0, 4, 0.5], scale: [0.85, 0.85, 0.85] },
  firstperson_righthand: { rotation: [0, -90, 25], translation: [1.13, 3.2, 1.13], scale: [0.68, 0.68, 0.68] },
  firstperson_lefthand: { rotation: [0, 90, -25], translation: [1.13, 3.2, 1.13], scale: [0.68, 0.68, 0.68] },
  ground: { rotation: [0, 0, 0], translation: [0, 2, 0], scale: [0.5, 0.5, 0.5] },
  fixed: { rotation: [0, 180, 0], translation: [0, 0, 0], scale: [1, 1, 1] },
};

// Plain steel models plus one variant per finish (name_finish), e.g. bayonet_fade. Keep in sync with Finish.java.
const models = { butterfly_handle_a: butterflyHandle(-1), butterfly_handle_b: butterflyHandle(1), halo: halo(), angel_wing: angelWing() };
const finishOf = {};
for (const finish of [null, ...Object.keys(FINISHES)]) {
  const suf = finish ? '_' + finish : '';
  models['bayonet' + suf] = bayonet(finish);
  models['karambit' + suf] = karambit(finish);
  models['butterfly_blade' + suf] = butterflyBlade(finish);
  models['butterfly' + suf] = [...models['butterfly_blade' + suf], ...models.butterfly_handle_a, ...models.butterfly_handle_b];
  for (const k of ['bayonet', 'karambit', 'butterfly_blade', 'butterfly']) finishOf[k + suf] = finish;
}

// ---------- Butterfly collision check ----------
// Moves the parts like KnifeType.java does (safe handle A fixed, blade turns about pin A, bite handle B about pin B
// on the blade) through every blade/bite angle in 0..180 and fails the build if any two parts' boxes overlap.
// Boxes only rotate about z, so each is an exact 2D rectangle plus a z interval. Touching (< EPS) is allowed.
function checkButterfly() {
  const EPS = 0.005, STEP = 5;
  const rotP = (p, o, a) => { const c = Math.cos(rad(a)), s = Math.sin(rad(a)); const dx = p[0] - o[0], dy = p[1] - o[1]; return [o[0] + dx * c - dy * s, o[1] + dx * s + dy * c]; };
  const shape = (e, moves) => {
    let pts = [[e.from[0], e.from[1]], [e.to[0], e.from[1]], [e.to[0], e.to[1]], [e.from[0], e.to[1]]];
    if (e.rotation) pts = pts.map(p => rotP(p, e.rotation.origin, e.rotation.angle));
    for (const m of moves) pts = pts.map(p => rotP(p, m.origin, m.angle));
    const cx = pts.reduce((s, p) => s + p[0], 0) / 4, cy = pts.reduce((s, p) => s + p[1], 0) / 4;
    const r = Math.max(...pts.map(p => Math.hypot(p[0] - cx, p[1] - cy)));
    return { e, pts, z: [e.from[2], e.to[2]], cx, cy, r };
  };
  const separated = (a, b) => {
    if (Math.min(a.z[1], b.z[1]) - Math.max(a.z[0], b.z[0]) <= EPS) return true;
    if (Math.hypot(a.cx - b.cx, a.cy - b.cy) > a.r + b.r) return true;
    for (const poly of [a.pts, b.pts]) for (let i = 0; i < 4; i++) {
      const p = poly[i], q = poly[(i + 1) % 4], nx = q[1] - p[1], ny = p[0] - q[0], len = Math.hypot(nx, ny);
      const proj = pts => pts.map(v => (v[0] * nx + v[1] * ny) / len);
      const pa = proj(a.pts), pb = proj(b.pts);
      if (Math.min(Math.max(...pa), Math.max(...pb)) - Math.max(Math.min(...pa), Math.min(...pb)) <= EPS) return true;
    }
    return false;
  };
  const exempt = (x, y) => (PIN.set.has(x) && TANG.set.has(y)) || (PIN.set.has(y) && TANG.set.has(x));
  const pA = tilt(BF.pinA), pB = tilt(BF.pinB);
  const A = models.butterfly_handle_a, B = models.butterfly_handle_b, Bl = models.butterfly_blade;
  let checked = 0;
  for (let blade = 0; blade <= 180; blade += STEP) for (let bite = 0; bite <= 180; bite += STEP) {
    const sa = A.map(e => shape(e, []));
    const sbl = Bl.map(e => shape(e, [{ origin: pA, angle: blade }]));
    const sb = B.map(e => shape(e, [{ origin: pB, angle: bite }, { origin: pA, angle: blade }]));
    for (const [n1, s1, n2, s2] of [['handle A', sa, 'blade', sbl], ['handle A', sa, 'handle B', sb], ['handle B', sb, 'blade', sbl]]) {
      for (const x of s1) for (const y of s2) {
        checked++;
        if (exempt(x.e, y.e) || separated(x, y)) continue;
        throw new Error(`butterfly parts collide at blade=${blade} bite=${bite}: ${n1} box ${JSON.stringify(x.e.from)}..${JSON.stringify(x.e.to)} vs ${n2} box ${JSON.stringify(y.e.from)}..${JSON.stringify(y.e.to)}`);
      }
    }
  }
  console.log(`butterfly collision check: ok (${checked} box pairs over ${(180 / STEP + 1) ** 2} poses)`);
}
checkButterfly();

fs.mkdirSync(path.join(RES, 'models/item'), { recursive: true });
fs.mkdirSync(path.join(RES, 'items'), { recursive: true });
fs.mkdirSync(path.join(RES, 'textures/item'), { recursive: true });

for (const [name, elements] of Object.entries(models)) {
  for (const e of elements) for (const v of [...e.from, ...e.to]) {
    if (v < -16 || v > 32) throw new Error(`${name}: coordinate ${v} out of range`);
  }
  const textures = { k: 'larper-client:item/knife', particle: 'larper-client:item/knife' };
  if (finishOf[name]) textures.f = 'larper-client:item/finish_' + finishOf[name];
  const model = { gui_light: 'front', textures, elements, display };
  fs.writeFileSync(path.join(RES, 'models/item', name + '.json'), JSON.stringify(model));
  fs.writeFileSync(path.join(RES, 'items', name + '.json'),
    JSON.stringify({ model: { type: 'minecraft:model', model: 'larper-client:item/' + name } }, null, 2));
  console.log(name.padEnd(20), elements.length, 'elements');
}

// ---------- texture + previews (tiny PNG writer) ----------
function png(w, h, rgba) {
  const crcTable = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
  const crc = b => { let c = 0xffffffff; for (const x of b) c = crcTable[(c ^ x) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
  const chunk = (type, data) => {
    const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
    const td = Buffer.concat([Buffer.from(type), data]);
    const c = Buffer.alloc(4); c.writeUInt32BE(crc(td));
    return Buffer.concat([len, td, c]);
  };
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (w * 4 + 1)] = 0; rgba.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4); }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}
const hex = h => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];

const tex = Buffer.alloc(16 * 16 * 4);
for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
  const [r, g, b] = hex(PALETTE[y >> 2][x >> 2]);
  const k = 1.06 - 0.04 * (y & 3); // slight sheen inside each swatch
  const i = (y * 16 + x) * 4;
  tex[i] = Math.min(255, r * k); tex[i + 1] = Math.min(255, g * k); tex[i + 2] = Math.min(255, b * k); tex[i + 3] = 255;
}
fs.writeFileSync(path.join(RES, 'textures/item/knife.png'), png(16, 16, tex));

// One FINISH_W x 4 strip per finish: column = position along the blade, row = flat/bevel/spine/fuller
for (const finish of Object.keys(FINISHES)) {
  const ft = Buffer.alloc(FINISH_W * 4 * 4);
  for (let row = 0; row < 4; row++) for (let x = 0; x < FINISH_W; x++) {
    const [r, g, b] = finishColor(finish, row, (x + 0.5) / FINISH_W);
    const i = (row * FINISH_W + x) * 4;
    ft[i] = r; ft[i + 1] = g; ft[i + 2] = b; ft[i + 3] = 255;
  }
  fs.writeFileSync(path.join(RES, 'textures/item/finish_' + finish + '.png'), png(FINISH_W, 4, ft));
}

const previewDir = process.argv[2];
if (previewDir) {
  fs.mkdirSync(previewDir, { recursive: true });
  const rotZ = (p, o, a) => { const c = Math.cos(rad(a)), s = Math.sin(rad(a)); const dx = p[0] - o[0], dy = p[1] - o[1]; return [o[0] + dx * c - dy * s, o[1] + dx * s + dy * c]; };
  const render = (elements, file, transforms = [], finish = null) => {
    const [vx, vy, vs] = (process.env.PREVIEW_VIEW || "-4,-4,24").split(",").map(Number); // model-space view box
    const S = 640, scale = S / vs;
    const img = Buffer.alloc(S * S * 4);
    for (let i = 0; i < S * S; i++) { img[i * 4] = 40; img[i * 4 + 1] = 44; img[i * 4 + 2] = 52; img[i * 4 + 3] = 255; }
    const polys = elements.map((e, idx) => {
      let pts = [[e.from[0], e.from[1]], [e.to[0], e.from[1]], [e.to[0], e.to[1]], [e.from[0], e.to[1]]];
      if (e.rotation) pts = pts.map(p => rotZ(p, e.rotation.origin, e.rotation.angle));
      for (const t of transforms[idx] || []) pts = pts.map(p => rotZ(p, t.origin, t.angle));
      const face = Object.values(e.faces)[0];
      if (face.texture === '#f') return { pts, z: e.to[2], col: finishColor(finish, Math.floor(face.uv[1] / 4), face.uv[0] / 16) };
      const [c, r] = face.uv.map(v => Math.floor(v / 4));
      return { pts, z: e.to[2], col: hex(PALETTE[r][c]) };
    }).sort((a, b) => a.z - b.z);
    for (const { pts, col, z } of polys) {
      const shade = 0.75 + (z - 7) * 0.12;
      const P = pts.map(([x, y]) => [(x - vx) * scale, S - (y - vy) * scale]);
      const minY = Math.max(0, Math.floor(Math.min(...P.map(p => p[1])))), maxY = Math.min(S - 1, Math.ceil(Math.max(...P.map(p => p[1]))));
      for (let y = minY; y <= maxY; y++) {
        const xs = [];
        for (let i = 0; i < 4; i++) {
          const a = P[i], b = P[(i + 1) % 4];
          if ((a[1] <= y + 0.5) !== (b[1] <= y + 0.5)) xs.push(a[0] + ((y + 0.5 - a[1]) / (b[1] - a[1])) * (b[0] - a[0]));
        }
        xs.sort((a, b) => a - b);
        for (let k = 0; k + 1 < xs.length; k += 2) for (let x = Math.max(0, Math.ceil(xs[k])); x <= Math.min(S - 1, Math.floor(xs[k + 1])); x++) {
          const i = (y * S + x) * 4;
          img[i] = Math.min(255, col[0] * shade); img[i + 1] = Math.min(255, col[1] * shade); img[i + 2] = Math.min(255, col[2] * shade);
        }
      }
    }
    fs.writeFileSync(path.join(previewDir, file), png(S, S, img));
  };
  render(models.angel_wing, 'angel_wing.png'); render(models.halo, 'halo.png');
  for (const n of ['bayonet', 'butterfly']) {
    render(models[n], n + '.png');
    for (const f of Object.keys(FINISHES)) render(models[n + '_' + f], `${n}_${f}.png`, [], f);
  }
  // Butterfly linkage like KnifeType.java: safe handle A fixed, blade turns about pin A, bite handle B turns about
  // pin B on the blade. Render several stages of closing to check nothing passes through anything.
  const nb = models.butterfly_blade.length, na = models.butterfly_handle_a.length;
  const pose = (blade, bite) => models.butterfly.map((_, i) => i < nb ? [{ origin: tilt(BF.pinA), angle: blade }]
    : i < nb + na ? [] : [{ origin: tilt(BF.pinB), angle: bite }, { origin: tilt(BF.pinA), angle: blade }]);
  for (const [blade, bite] of [[0, 90], [0, 180], [90, 180], [180, 180]]) render(models.butterfly, `butterfly_b${blade}_h${bite}.png`, pose(blade, bite));
  console.log('previews ->', previewDir);
}
