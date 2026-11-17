import os, re, sys
src_root, out_root = sys.argv[1], sys.argv[2]
FRAMES = {
    'DaxScaffold': 'ScreenshotFrame.Fixed(widthDp = 360, heightDp = 320)',
    'DaxBottomSheetDialog': 'ScreenshotFrame.Screen(widthDp = 360, heightDp = 640)',
    'DaxActionBottomSheetDialog': 'ScreenshotFrame.Screen(widthDp = 360, heightDp = 640)',
    'DaxPromoBottomSheetDialog': 'ScreenshotFrame.Screen(widthDp = 360, heightDp = 640)',
    'DaxContextMenu': 'ScreenshotFrame.Screen(widthDp = 360, heightDp = 280)',
}
DROP = {'android.annotation.SuppressLint','androidx.compose.ui.tooling.preview.Preview','com.android.tools.screenshot.PreviewTest'}
entries = []
for dp, _, fs in os.walk(src_root):
    for f in sorted(fs):
        if not f.endswith('Test.kt'): continue
        name = f[:-len('Test.kt')]
        s = open(os.path.join(dp, f)).read()
        header = s[:s.index('package ')]
        pkg = re.search(r'^package (\S+)', s, re.M).group(1)
        imports = [i for i in re.findall(r'^import (\S+)$', s, re.M) if i not in DROP]
        m = re.search(r'((?:^@.*\n)+)fun ' + name + r'Test\(\) \{\n', s, re.M)
        annots = m.group(1).splitlines()
        optins = [a for a in annots if a.startswith('@OptIn')]
        others = [a for a in annots if not re.match(r'@(SuppressLint|OptIn|PreviewTest|Preview|Composable)\b', a)]
        assert not others, (name, others)
        # body until matching brace
        i = m.end(); d = 1; j = i
        while d:
            if s[j] == '{': d += 1
            elif s[j] == '}': d -= 1
            j += 1
        body = s[i:j-1].rstrip('\n')
        rest = s[j:].strip('\n')
        body = '\n'.join(('    ' + l) if l.strip() else '' for l in body.splitlines())
        imports += ['com.duckduckgo.common.ui.compose.screenshot.ScreenshotCase']
        frame = FRAMES.get(name)
        if frame: imports.append('com.duckduckgo.common.ui.compose.screenshot.ScreenshotFrame')
        if 'androidx.compose.runtime.Composable' not in imports: imports.append('androidx.compose.runtime.Composable')
        imports = sorted(set(imports), key=lambda x: x)
        ctor = f'name = "{name}"' + (f', frame = {frame}' if frame else '')
        out = header + f'package {pkg}\n\n' + ''.join(f'import {x}\n' for x in imports) + '\n'
        out += f'object {name}Screenshot : ScreenshotCase({ctor}) {{\n'
        for o in optins: out += f'    {o}\n'
        out += f'    @Composable\n    override fun Content() {{\n{body}\n    }}\n}}\n'
        if rest: out += '\n' + rest + '\n'
        rel = os.path.relpath(dp, src_root)
        os.makedirs(os.path.join(out_root, rel), exist_ok=True)
        open(os.path.join(out_root, rel, f'{name}Screenshot.kt'), 'w').write(out)
        entries.append((name, pkg))
entries.sort()
print(len(entries))
agg = header + 'package com.duckduckgo.common.ui.compose.screenshot\n\n'
agg += ''.join(f'import {p}.{n}Screenshot\n' for n, p in sorted(entries, key=lambda e: f'{e[1]}.{e[0]}'))
agg += '\n/** Every design system screenshot. Each screenshot testing library renders all of them. */\n'
agg += 'val designSystemScreenshots: List<ScreenshotCase> = listOf(\n' + ''.join(f'    {n}Screenshot,\n' for n, _ in entries) + ')\n'
open(os.path.join(out_root, 'com/duckduckgo/common/ui/compose/screenshot/DesignSystemScreenshots.kt'), 'w').write(agg)
