import base64, io, itertools, json, os, sys, html
import numpy as np
from PIL import Image
sys.path.insert(0, os.path.dirname(__file__))
module_dir, out_html = sys.argv[1], sys.argv[2]
import collect
imgs = collect.find() if hasattr(collect, 'find') else None
LIBS = ['Roborazzi', 'Paparazzi', 'Compose Preview']
PAIRS = list(itertools.combinations(LIBS, 2))

def load(p):
    return np.asarray(Image.open(p).convert('RGBA')).astype(np.int16)

def flatten(a):
    # Composite onto white so a transparent pixel and a white pixel compare equal visually.
    rgb, al = a[..., :3], a[..., 3:4] / 255.0
    return (rgb * al + 255 * (1 - al)).round().astype(np.int16)

def b64(img):
    buf = io.BytesIO(); img.save(buf, 'PNG', optimize=True)
    return 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()

def diff_image(a, b):
    h, w = max(a.shape[0], b.shape[0]), max(a.shape[1], b.shape[1])
    def pad(x):
        out = np.full((h, w, 3), -1, np.int16); out[:x.shape[0], :x.shape[1]] = flatten(x); return out
    pa, pb = pad(a), pad(b)
    outside = (pa[..., 0] < 0) ^ (pb[..., 0] < 0)
    changed = (np.abs(pa - pb).max(axis=2) > 0) & ~outside
    base = np.where(pa[..., :1] < 0, pb, pa).clip(0, 255)
    gray = (base.mean(axis=2, keepdims=True) * 0.25 + 255 * 0.75).repeat(3, axis=2)
    gray[changed] = [230, 0, 120]
    yy, xx = np.mgrid[0:h, 0:w]
    stripes = ((xx + yy) // 6) % 2 == 0
    gray[outside & stripes] = [255, 160, 0]
    gray[outside & ~stripes] = [255, 225, 170]
    return Image.fromarray(gray.astype(np.uint8)), int(changed.sum()), int(outside.sum())

def solid_area_pct(delta, tile=8):
    # Share of the image covered by 8×8 blocks where most pixels changed. Anti-aliased text changes scattered edge pixels and
    # leaves almost no such block, while a shadow, a highlight or a moved element changes whole areas.
    h, w = delta.shape[0] // tile * tile, delta.shape[1] // tile * tile
    blocks = (delta[:h, :w] > 8).reshape(h // tile, tile, w // tile, tile).mean(axis=(1, 3)) > 0.5
    return float(blocks.sum() * tile * tile / delta.size * 100)

def compare(a, b):
    same_size = a.shape == b.shape
    if same_size:
        exact = bool((a == b).all())
        fa, fb = flatten(a), flatten(b)
        delta = np.abs(fa - fb).max(axis=2)
        return dict(same_size=True, exact=exact, visual=bool((delta == 0).all()),
                    pct=float((delta > 0).mean() * 100), max_delta=int(delta.max()),
                    pct_over_16=float((delta > 16).mean() * 100), pct_over_48=float((delta > 48).mean() * 100),
                    solid_pct=solid_area_pct(delta))
    return dict(same_size=False, exact=False, visual=False, pct=100.0, max_delta=255, pct_over_16=100.0, pct_over_48=100.0,
                solid_pct=100.0, sizes=[f'{a.shape[1]}×{a.shape[0]}', f'{b.shape[1]}×{b.shape[0]}'])

rows = []
for name in sorted(imgs):
    paths = imgs[name]
    missing = [l for l in LIBS if l not in paths]
    arr = {l: load(paths[l]) for l in LIBS if l in paths}
    pairs = {}
    for l1, l2 in PAIRS:
        if l1 in arr and l2 in arr:
            pairs[(l1, l2)] = compare(arr[l1], arr[l2])
    all_same = not missing and all(p['exact'] for p in pairs.values())
    all_visual = not missing and all(p['visual'] for p in pairs.values())
    rows.append(dict(name=name, paths=paths, arr=arr, pairs=pairs, missing=missing, all_same=all_same, all_visual=all_visual))

def size(a): return f'{a.shape[1]}×{a.shape[0]}'

def verdict(r):
    if r['all_same']: return ('same', 'Identical')
    if r['all_visual']: return ('visual', 'Same on white (alpha differs)')
    return ('diff', 'Different')

# How noticeable a difference is: "Visible" needs solid areas of changed pixels (see solid_area_pct), not just scattered edges.
VISIBLE_MIN_AREA_PCT = 0.1
TIERS = [('same', 'Identical'), ('tiny', 'Imperceptible'), ('minor', 'Minor'), ('major', 'Visible'), ('size', 'Different size')]

def pct(v):
    return f'{v:.1f}%' if v >= 0.1 else 'under 0.1%'

def tier(p):
    if not p['same_size']: return ('size', 'Different size', f"{p['sizes'][0]} vs {p['sizes'][1]}")
    if p['exact']: return ('same', 'Identical', 'every pixel matches')
    if p['visual']: return ('same', 'Identical', 'matches once drawn on white')
    if p['max_delta'] <= 3: return ('tiny', 'Imperceptible', f"colours off by a shade on {pct(p['pct'])} of the image")
    if p['solid_pct'] < VISIBLE_MIN_AREA_PCT: return ('minor', 'Minor', f"only edges differ, on {pct(p['pct'])} of the image")
    return ('major', 'Visible', f"solid areas covering {pct(p['solid_pct'])} of the image differ")

def pair_text(p):
    key, label, detail = tier(p)
    raw = '' if not p['same_size'] else f"{p['pct']:.2f}% of pixels differ, solid areas cover {p['solid_pct']:.2f}%, largest channel difference {p['max_delta']} of 255"
    return f'<span class="tier t-{key}" title="{raw}">{label}</span> <span class="detail">{detail}</span>'

n_same = sum(r['all_same'] for r in rows); n_vis = sum((not r['all_same']) and r['all_visual'] for r in rows); n_diff = len(rows) - n_same - n_vis
# pairwise agreement counts
agree = {pr: sum(1 for r in rows if pr in r['pairs'] and r['pairs'][pr]['exact']) for pr in PAIRS}
agree_vis = {pr: sum(1 for r in rows if pr in r['pairs'] and r['pairs'][pr]['visual']) for pr in PAIRS}
size_agree = {pr: sum(1 for r in rows if pr in r['pairs'] and r['pairs'][pr]['same_size']) for pr in PAIRS}

summary = dict(total=len(rows), identical=n_same, visual_only=n_vis, different=n_diff,
               pairs={f'{a} vs {b}': dict(identical=agree[(a, b)], identical_on_white=agree_vis[(a, b)], same_size=size_agree[(a, b)]) for a, b in PAIRS},
               cases={r['name']: dict(verdict=verdict(r)[1], pairs={f'{a} vs {b}': {k: v for k, v in p.items()} for (a, b), p in r['pairs'].items()}) for r in rows})
json.dump(summary, open(os.path.splitext(out_html)[0] + '.json', 'w'), indent=1)

css = """
:root{--bg:#fff;--fg:#1c1c1c;--muted:#666;--card:#f6f6f4;--line:#e3e3df;--same:#1f7a3a;--samebg:#e3f4e8;--vis:#8a5a00;--visbg:#fbf0d9;--diff:#b0103c;--diffbg:#fbe3ea;--checker:#e9e9e9}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){--bg:#161616;--fg:#ececec;--muted:#a0a0a0;--card:#202020;--line:#333;--same:#6fd38c;--samebg:#183323;--vis:#f1c060;--visbg:#3a2d12;--diff:#ff7a9c;--diffbg:#3d1622;--checker:#2a2a2a}}
:root[data-theme="dark"]{--bg:#161616;--fg:#ececec;--muted:#a0a0a0;--card:#202020;--line:#333;--same:#6fd38c;--samebg:#183323;--vis:#f1c060;--visbg:#3a2d12;--diff:#ff7a9c;--diffbg:#3d1622;--checker:#2a2a2a}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--fg);font:15px/1.5 system-ui,-apple-system,sans-serif}
main{max-width:1200px;margin:0 auto;padding:32px 16px 80px}h1{font-size:28px;margin:0 0 4px}h2{font-size:18px;margin:0}
.muted{color:var(--muted)}.scroll{overflow-x:auto;margin:12px 0 28px}.scroll table{margin:0;min-width:640px}.stats{display:flex;gap:12px;flex-wrap:wrap;margin:20px 0}.stat{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:12px 16px;min-width:150px}
.stat b{display:block;font-size:26px}table{border-collapse:collapse;width:100%;margin:12px 0 28px;font-size:14px}th,td{text-align:left;padding:6px 10px;border-bottom:1px solid var(--line)}
.badge{display:inline-block;border-radius:999px;padding:1px 10px;font-size:12px;font-weight:600}.same{color:var(--same);background:var(--samebg)}.visual{color:var(--vis);background:var(--visbg)}.diff{color:var(--diff);background:var(--diffbg)}
.filters{position:sticky;top:0;background:var(--bg);padding:10px 0;z-index:2;display:flex;gap:8px;flex-wrap:wrap;border-bottom:1px solid var(--line)}
.filters button{font:inherit;border:1px solid var(--line);background:var(--card);color:var(--fg);border-radius:999px;padding:4px 14px;cursor:pointer}.filters button[aria-pressed=true]{border-color:var(--fg)}
.case{border:1px solid var(--line);border-radius:12px;margin:18px 0;overflow:hidden}.case>header{display:flex;gap:12px;align-items:center;justify-content:space-between;flex-wrap:wrap;padding:12px 16px;background:var(--card)}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:14px;padding:16px;align-items:start}
figure{margin:0}figcaption{font-size:12px;color:var(--muted);margin-bottom:6px}figcaption b{color:var(--fg)}
.img{background:repeating-conic-gradient(var(--checker) 0 25%,transparent 0 50%) 0 0/16px 16px;border:1px solid var(--line);border-radius:6px;display:inline-block;max-width:100%}
.img img{display:block;max-width:100%;height:auto;image-rendering:pixelated}
.tier{display:inline-block;border-radius:4px;padding:0 6px;font-size:12px;font-weight:600;white-space:nowrap}.detail{color:var(--muted);font-size:13px}
.t-same{color:var(--same);background:var(--samebg)}.t-tiny{color:var(--same);background:transparent;border:1px solid var(--samebg)}.t-minor{color:var(--vis);background:var(--visbg)}.t-major,.t-size{color:var(--diff);background:var(--diffbg)}
.libs{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:12px;margin:16px 0}.lib{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:12px 16px}.lib b{display:block;font-size:16px}.lib span{display:block;color:var(--muted);font-size:13px}
.setup{margin:0 0 8px;padding-left:20px}.setup li{margin:2px 0}.callout{border:1px solid var(--line);border-left:4px solid var(--vis);background:var(--card);border-radius:8px;padding:12px 16px;margin:20px 0}.callout h2{margin-bottom:6px}.callout ul{margin:6px 0 0;padding-left:20px}
dl.tiers{display:grid;grid-template-columns:max-content 1fr;gap:6px 12px;font-size:14px;margin:8px 0 4px}dl.tiers dt{margin:0}dl.tiers dd{margin:0;color:var(--muted)}
.summary{display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:14px;margin:8px 0 28px}.summary section{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:12px 16px}.summary h4{margin:0 0 8px;font-size:15px}.summary ul{margin:0;padding-left:18px}.summary li{margin:3px 0}.summary .group{margin:6px 0;font-size:14px}h3{font-size:16px;margin:0 0 4px}
.diffs{border-top:1px solid var(--line)}.legend{font-size:12px;color:var(--muted);padding:0 16px 14px}.sw{display:inline-block;width:10px;height:10px;border-radius:2px;vertical-align:middle;margin:0 4px 0 10px}
details summary{cursor:pointer;padding:10px 16px;font-size:13px}code{font-size:13px}
"""
out = [f'<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="description" content="Roborazzi, Paparazzi and Compose Preview Screenshot Testing rendering the same 57 DuckDuckGo design-system composables, compared pixel by pixel."><title>Screenshot Library Comparison</title><style>{css}</style></head><body><main>']
out.append(f'<h1>Screenshot library comparison</h1><p class="muted">The same {len(rows)} design-system composables, rendered from one shared catalog by three screenshot testing libraries.</p>')
out.append('''<div class="libs">
<div class="lib"><b>Roborazzi</b><span>1.77.0</span><span>Renders with Robolectric's native graphics</span></div>
<div class="lib"><b>Paparazzi</b><span>2.0.0-alpha05.1</span><span>Renders with layoutlib</span></div>
<div class="lib"><b>Compose Preview Screenshot Testing</b><span>0.0.1-alpha17</span><span>Renders with layoutlib, like Android Studio previews</span></div>
</div>
<p>Every library uses the same setup:</p><ul class="setup">
<li>Each case is drawn on a white background through the same <code>ScreenshotFrame</code>.</li>
<li>The screen is 360×640dp at 240dpi (hdpi). Paparazzi shrinks anything larger than 1000px, so a denser screen couldn't be compared pixel for pixel.</li>
<li>The DuckDuckGo light theme (<code>Theme.DuckDuckGo.Light</code>) is applied in all three.</li>
</ul>''')
PAPARAZZI_WAIT = '''<li id="paparazzi-wait"><b>Paparazzi has to wait for animated widgets before capturing.</b> It captures the first frame, so anything that animates in comes out missing:<ul>
<li><b>Dropdown menus</b> (<a href="#DaxContextMenu">DaxContextMenu</a>) fade in from transparent. <b>Workaround:</b> the test composes through a <code>Recomposer</code> with a zero <code>MotionDurationScale</code>, so animations finish on their first frame. Don't cancel that recomposer between tests, or later tests in the same JVM get stuck on the first frame again. This also moves <a href="#DaxProgressSpinner">DaxProgressSpinner</a> to a different point of its endless animation.</li>
<li><b>Bottom sheets</b> (<a href="#DaxBottomSheetDialog">DaxBottomSheetDialog</a>, <a href="#DaxActionBottomSheetDialog">Action</a>, <a href="#DaxPromoBottomSheetDialog">Promo</a>) open themselves after a layout pass that Paparazzi never runs. <b>Workaround:</b> their catalog cases pass <code>rememberExpandedSheetState()</code>, which starts open.</li>
</ul></li>'''
out.append('''<h2>What the differences are</h2><ul>
<li><b>Paparazzi and Compose Preview share a renderer.</b> Both use layoutlib, so they match far more often than either matches Roborazzi, which uses Robolectric's native graphics.</li>
''' + PAPARAZZI_WAIT + '''<li id="paparazzi-focus"><b>Paparazzi has to be put in touch mode.</b> Layoutlib never flags new windows as in touch mode, so Paparazzi's dialogs and popups focus their first control, as for a keyboard or D-pad user; the other two libraries render in touch mode. <b>Workaround:</b> the test wraps layoutlib's window session (<code>openWindowsInTouchMode()</code>) so every new window reports touch mode. This removed focus highlights from <a href="#DaxContextMenu">DaxContextMenu</a>, <a href="#DaxActionBottomSheetDialog">DaxActionBottomSheetDialog</a> and <a href="#DaxPromoBottomSheetDialog">DaxPromoBottomSheetDialog</a>. The last one exposed a <code>DaxButton</code> issue: the focus layer uses the opaque pressed colour, so a focused ghost button is solid blue with blue text, unreadable for keyboard and D-pad users.</li>
<li><b>Bottom sheets</b> (<a href="#DaxBottomSheetDialog">DaxBottomSheetDialog</a>, <a href="#DaxActionBottomSheetDialog">Action</a>, <a href="#DaxPromoBottomSheetDialog">Promo</a>): all three draw the scrim and sheet. Starting the sheets open left Roborazzi's and Compose Preview's images byte-identical, so it matches their animated end state. Compose Preview's scrim is a slightly different shade.</li>
<li><b><a href="#DaxContextMenu">DaxContextMenu</a></b>: all three draw the menu with its icons, but Roborazzi leaves out the shadow. Compose Preview only shows the icons because its test hosts each case in a view with the app theme (<code>ThemedScreenshotHost</code>): the icons use <code>?attr/daxColor*</code>, and a popup takes its theme from its host view.</li>
<li><b>Text</b>: Roborazzi rasterises glyphs and spaces lines slightly differently, so most text differs by anti-aliasing. <a href="#DaxAppTPBanner">DaxAppTPBanner</a> ends up 6px taller and <a href="#DaxBrandButton">DaxBrandButton</a> 2px wider.</li>
<li><b>Flat colours</b>: many Roborazzi differences are colours off by one shade, invisible and marked Imperceptible (for example <a href="#DaxSwitch">DaxSwitch</a>, <a href="#DaxSlider">DaxSlider</a> and the skeletons).</li>
</ul>''')
out.append('''<h2>How to read the comparisons</h2><dl class="tiers">
<dt><span class="tier t-same">Identical</span></dt><dd>Every pixel matches.</dd>
<dt><span class="tier t-tiny">Imperceptible</span></dt><dd>Some colours are off by a shade or two; you can't see it.</dd>
<dt><span class="tier t-minor">Minor</span></dt><dd>Only edges and fine detail differ, such as softer or sharper text, or a slightly different shade.</dd>
<dt><span class="tier t-major">Visible</span></dt><dd>Whole areas differ, such as a missing shadow, a highlighted row or a filled button.</dd>
<dt><span class="tier t-size">Different size</span></dt><dd>The images have different dimensions, so content has moved.</dd>
</dl><p class="muted">Hover a label for the exact pixel numbers.</p>''')
out.append('<h2>Pairwise agreement</h2><div class="scroll"><table><thead><tr><th>Pair</th>' + ''.join(f'<th>{label}</th>' for _, label in TIERS) + '</tr></thead><tbody>')
for a, b in PAIRS:
    counts = {k: sum(1 for r in rows if (a, b) in r['pairs'] and tier(r['pairs'][(a, b)])[0] == k) for k, _ in TIERS}
    out.append(f'<tr><td>{a} vs {b}</td>' + ''.join(f'<td>{counts[k]}</td>' for k, _ in TIERS) + '</tr>')
out.append('</tbody></table></div>')

SEVERITY = {k: i for i, (k, _) in enumerate(TIERS)}
VISIBLE = {'major', 'size'}
visible_cases, similar_cases = [], []
for r in rows:
    tiers = {pr: tier(p) for pr, p in r['pairs'].items()}
    worst = max(tiers.values(), key=lambda t: SEVERITY[t[0]])
    if worst[0] in VISIBLE:
        visible_cases.append((r['name'], [(pr, t) for pr, t in tiers.items() if t[0] in VISIBLE]))
    else:
        similar_cases.append((r['name'], worst))
out.append(f'<h3>Summary</h3><p>A case counts as <b>visibly different</b> when at least one pair of libraries is Visible or Different size, and as <b>roughly the same</b> when every pair is Identical, Imperceptible or Minor.</p>')
out.append(f'<div class="summary"><section><h4><span class="tier t-major">Visibly different</span> {len(visible_cases)} of {len(rows)}</h4><ul>')
for name, diffs in visible_cases:
    detail = '; '.join(f'{a} vs {b}: {label.lower()}' for (a, b), (_, label, _) in diffs)
    out.append(f'<li><a href="#{name}">{name}</a> <span class="detail">{detail}</span></li>')
out.append('</ul></section>')
out.append(f'<section><h4><span class="tier t-same">Roughly the same</span> {len(similar_cases)} of {len(rows)}</h4>')
for key, label in TIERS[:3]:
    names = [n for n, w in similar_cases if w[0] == key]
    if names:
        heading = 'Identical in all three' if key == 'same' else f'At most {label.lower()}'
        out.append(f'<p class="group"><b>{heading}</b> ({len(names)}): ' + ', '.join(f'<a href="#{n}">{n}</a>' for n in names) + '</p>')
out.append('</section></div>')
out.append('<h2>All cases</h2><div class="scroll"><table><thead><tr><th>Case</th><th>Result</th>' + ''.join(f'<th>{a} vs {b}</th>' for a, b in PAIRS) + '</tr></thead><tbody>')
for r in rows:
    cls, label = verdict(r)
    out.append(f'<tr><td><a href="#{r["name"]}">{r["name"]}</a></td><td><span class="badge {cls}">{label}</span></td>' + ''.join(f'<td>{pair_text(r["pairs"][pr]) if pr in r["pairs"] else "missing"}</td>' for pr in PAIRS) + '</tr>')
out.append('</tbody></table></div>')
out.append('<div class="filters" role="group" aria-label="Filter cases"><button data-f="all" aria-pressed="true">All</button><button data-f="diff" aria-pressed="false">Different</button><button data-f="visual" aria-pressed="false">Alpha only</button><button data-f="same" aria-pressed="false">Identical</button></div>')
for r in rows:
    cls, label = verdict(r)
    out.append(f'<section class="case" id="{r["name"]}" data-k="{cls}"><header><h2>{r["name"]}</h2><span class="badge {cls}">{label}</span></header>')
    if r['all_same']:
        a = r['arr']['Roborazzi']
        out.append(f'<div class="grid"><figure><figcaption><b>All three</b> · {size(a)}</figcaption><span class="img"><img loading="lazy" src="{b64(Image.open(r["paths"]["Roborazzi"]))}" alt="{r["name"]} as rendered by all three libraries"></span></figure></div></section>')
        continue
    out.append('<div class="grid">')
    for l in LIBS:
        if l in r['arr']:
            out.append(f'<figure><figcaption><b>{l}</b> · {size(r["arr"][l])}</figcaption><span class="img"><img loading="lazy" src="{b64(Image.open(r["paths"][l]))}" alt="{r["name"]} rendered by {l}"></span></figure>')
        else:
            out.append(f'<figure><figcaption><b>{l}</b></figcaption><p class="muted">No image</p></figure>')
    out.append('</div>')
    diffs = [(pr, p) for pr, p in r['pairs'].items() if not p['exact']]
    if diffs:
        out.append('<div class="diffs"><div class="grid">')
        for (l1, l2), p in diffs:
            img, changed, outside = diff_image(r['arr'][l1], r['arr'][l2])
            out.append(f'<figure><figcaption><b>{l1} vs {l2}</b> · {pair_text(p)}</figcaption><span class="img"><img loading="lazy" src="{b64(img)}" alt="Differences between {l1} and {l2} for {r["name"]}"></span></figure>')
        out.append('</div><div class="legend">Diff:<span class="sw" style="background:#e60078"></span>pixel differs<span class="sw" style="background:#ffa000"></span>only one image covers this area (sizes differ, aligned top-left)</div></div>')
    out.append('</section>')
out.append("""<script>
document.querySelectorAll('.filters button').forEach(b=>b.addEventListener('click',()=>{
document.querySelectorAll('.filters button').forEach(x=>x.setAttribute('aria-pressed',x===b));
const f=b.dataset.f;document.querySelectorAll('.case').forEach(c=>c.hidden=!(f==='all'||c.dataset.k===f));}));
</script></main></body></html>""")
open(out_html, 'w').write('\n'.join(out))
print(f'identical={n_same} alpha_only={n_vis} different={n_diff}')
for pr in PAIRS: print(pr, 'same_size', size_agree[pr], 'identical', agree[pr], 'on_white', agree_vis[pr])
print('size', os.path.getsize(out_html) // 1024, 'KB')
