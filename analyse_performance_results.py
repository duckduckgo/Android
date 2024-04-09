import re
import sys
import json
from urllib.parse import urlparse

FILENAME = 'perf-results.txt'

def print_results(results):
    print(f'\nTBT: {len(results)} results\n')
    for result in results:
        print(result['tbt'])

    print(f'\nLCP: {len(results)} results\n')
    for result in results:
        print(result['lcp'])

    print(f'\nLONGTASKS: {len(results)} results\n')
    for result in results:
        print(result['longTasksNum'])


def parse_results(line):
    result = json.loads(re.findall(r'{.*}', line)[0])
    url = re.findall(r'METRICS: ([^{]+) {', line)[0]
    # hostname = urlparse(url).hostname
    return url, result

configs = [
    'NOJS',
    'CSSONLY',
    'ELEMENTHIDINGONLY',
    'NATIVECSSHARNESSONLY',
    'CPMONLY',
    'AUTOFILLONLY',
    'ADDDOCUMENTSTARTJS',
    'ADDDOCUMENTSTARTDISABLED',
    'DEFAULT',
]
results = {}
urls = [
    # 'https://edition.cnn.com/2024/04/01/world/total-solar-eclipse-phases-scn/index.html',
    # 'https://m.twitch.tv/epicnpcman',
    # 'https://www.bbc.com/sport/formula1/68591119',
    'https://en.m.wikipedia.org/wiki/Hayao_Miyazaki',
    # 'https://eu.usatoday.com/story/money/2024/03/28/sam-bankman-fried-sentencing-ftx-crypto-founder/73128640007/',
    'https://www.okcupid.com/',
    # 'https://www.roboform.com/filling-test-all-fields',
]

with open(FILENAME, 'r') as f:
    line = f.readline()
    while line:
        if 'PERF METRICS INJECTED' in line:
            found = False
            for config in configs:
                if config in line:
                    found = True
                    line = f.readline()
                    if 'PerfMetricsInterface' not in line and config in line:
                        # page has reloaded
                        print('skipping line for', config)
                        line = f.readline()
                    url, result = parse_results(line)
                    if config not in results:
                        results[config] = {}
                    if url not in results[config]:
                        results[config][url] = []
                    results[config][url].append(result)
            if not found:
                print('ERROR: Unknown config', line)
                sys.exit(1)
        line = f.readline()

for url in urls:
    print(f'\nURL: {url}')

    for config, result in results.items():
        print(f'\n{config}:')
        print_results(result[url])
        input('\nPress Enter to continue...')
