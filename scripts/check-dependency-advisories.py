"""Query OSV using public dependency coordinates only; no account configuration is read.

Run :app:writeReleaseDependencyInventory first. Python 3.11+; standard library only.
An empty result is limited to OSV's current indexed advisories, not a security audit.
"""
import argparse
import datetime
import json
from pathlib import Path
import tomllib
import urllib.request


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inventory', type=Path, default=Path('app/build/reports/acceptance/release-dependencies.json'))
    parser.add_argument('--output', type=Path, default=Path('app/build/reports/acceptance/dependency-advisories.json'))
    args = parser.parse_args()
    packages = [p for p in json.loads(args.inventory.read_text(encoding='utf-8')) if p['version'] != 'unspecified']
    lock = tomllib.loads(Path('native/code-reader/Cargo.lock').read_text(encoding='utf-8'))
    packages += [dict(ecosystem='crates.io', name=p['name'], version=p['version'])
                 for p in lock['package'] if p.get('source', '').startswith('registry+')]
    results = []
    for offset in range(0, len(packages), 100):
        batch = packages[offset:offset + 100]
        payload = {'queries': [{'package': {'ecosystem': p['ecosystem'], 'name': p['name']},
                                'version': p['version']} for p in batch]}
        request = urllib.request.Request('https://api.osv.dev/v1/querybatch',
                                         data=json.dumps(payload).encode('utf-8'),
                                         headers={'Content-Type': 'application/json'})
        with urllib.request.urlopen(request, timeout=45) as response:
            answers = json.load(response)['results']
        if len(answers) != len(batch):
            raise RuntimeError('Incomplete OSV response; scan is not valid.')
        results.extend(dict(**p, advisories=a.get('vulns', [])) for p, a in zip(batch, answers))
    report = dict(checkedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),
                  source='https://api.osv.dev/v1/querybatch',
                  scope='Resolved release Maven runtime coordinates and Cargo.lock registry packages. '
                        'Build tooling, Android OS, unindexed vulnerabilities and reachability are not assessed.',
                  packages=results)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    findings = [p for p in results if p['advisories']]
    print(f'Checked {len(results)} coordinates; {len(findings)} have indexed advisories. Report: {args.output}')
    for finding in findings:
        print(f"{finding['ecosystem']} {finding['name']} {finding['version']}: " +
              ', '.join(v['id'] for v in finding['advisories']))
    return 1 if findings else 0


if __name__ == '__main__':
    raise SystemExit(main())
