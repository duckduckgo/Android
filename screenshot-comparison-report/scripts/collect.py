import glob, os, re, sys, json
from PIL import Image
m = sys.argv[1]
def find():
    out = {}
    for p in glob.glob(f'{m}/src/test/screenshots/roborazzi/*.png'):
        out.setdefault(os.path.basename(p)[:-4], {})['Roborazzi'] = p
    for p in glob.glob(f'{m}/src/test/snapshots/images/*.png'):
        n = re.search(r'screenshot\[(\w+)\]', p).group(1); out.setdefault(n, {})['Paparazzi'] = p
    for p in glob.glob(f'{m}/src/screenshotTestDefaultDebug/reference/**/*.png', recursive=True):
        n = re.search(r'_([A-Za-z0-9]+)\.png$', p).group(1); out.setdefault(n, {})['Compose Preview'] = p
    return out
imgs = find()
if __name__ == '__main__':
    for n in sorted(imgs):
        print(f'{n:34}', '  '.join(f'{k[:4]}={Image.open(v).size}' for k, v in sorted(imgs[n].items())))
