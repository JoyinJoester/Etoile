"""Generate offline notices from resolved coordinates, public POMs and local package notices.

Run :app:writeReleaseDependencyInventory first. This tool reads public build artifacts,
never local signing/account configuration. Maven metadata is fetched only from public
Google Maven and Maven Central when absent from the Gradle cache. Review generated
license declarations before distribution; this is not a legal compatibility opinion.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import io
import json
import os
from pathlib import Path
import re
import tomllib
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

NS = {'p': 'http://maven.apache.org/POM/4.0.0'}
ROOT = Path(__file__).resolve().parent.parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--gradle-cache', type=Path, default=Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle')))
    parser.add_argument('--cargo-registry', type=Path, default=Path(os.environ.get('CARGO_HOME', Path.home() / '.cargo')) / 'registry/src')
    args = parser.parse_args()
    os.chdir(ROOT)
    cache = args.gradle_cache / 'caches/modules-2/files-2.1'
    pom_cache = ROOT / '.codex-tmp/license-poms'
    pom_cache.mkdir(parents=True, exist_ok=True)
    destination = ROOT / 'docs/legal/dependency-texts'
    destination.mkdir(parents=True, exist_ok=True)
    packages = json.loads(Path('app/build/reports/acceptance/release-dependencies.json').read_text(encoding='utf-8'))

    def pom(coordinate):
        group, artifact, version = coordinate
        leaf = f'{artifact}-{version}.pom'
        local = list((cache / group / artifact / version).glob(f'*/{leaf}'))
        if local:
            return local[0].read_bytes(), f'Maven POM: {group}:{artifact}:{version}'
        cached = pom_cache / (hashlib.sha256(':'.join(coordinate).encode()).hexdigest() + '.pom')
        repositories = ['https://dl.google.com/dl/android/maven2/', 'https://repo.maven.apache.org/maven2/'] if group.startswith('androidx.') else ['https://repo.maven.apache.org/maven2/', 'https://dl.google.com/dl/android/maven2/']
        relative = group.replace('.', '/') + f'/{artifact}/{version}/{leaf}'
        if cached.exists():
            return cached.read_bytes(), repositories[0] + relative
        for repository in repositories:
            try:
                with urllib.request.urlopen(repository + relative, timeout=30) as response:
                    data = response.read(1_000_001)
                if len(data) > 1_000_000:
                    raise ValueError('Oversized public POM')
                ET.fromstring(data)
                cached.write_bytes(data)
                return data, repository + relative
            except urllib.error.HTTPError as error:
                if error.code != 404:
                    raise
        raise RuntimeError(f'No POM for {coordinate}')

    def text_blob(text):
        # Keep copyright and license content intact while normalizing line endings.
        text = text.replace('\r\n', '\n').strip() + '\n'
        digest = hashlib.sha256(text.encode('utf-8')).hexdigest()
        (destination / (digest + '.txt')).write_text(text, encoding='utf-8')
        return digest

    def archive_notices(path):
        found = []
        def inspect(source, nested=False):
            with zipfile.ZipFile(source) as archive:
                for entry in archive.infolist():
                    name = entry.filename
                    if not nested and name == 'classes.jar' and entry.file_size < 50_000_000:
                        inspect(io.BytesIO(archive.read(entry)), True)
                    leaf = name.rsplit('/', 1)[-1]
                    if re.fullmatch(r'(?:LICENSE|LICENCE|NOTICE|COPYING)(?:[._-].*)?', leaf, re.I) and entry.file_size < 512_000:
                        try:
                            content = archive.read(entry).decode('utf-8')
                        except UnicodeDecodeError:
                            continue
                        found.append({'source': path.name + ('!classes.jar' if nested else '') + '!' + name,
                                      'text': text_blob(content)})
        inspect(path)
        return found

    def maven_package(package):
        group, artifact = package['name'].split(':', 1)
        coordinate = group, artifact, package['version']
        data, metadata_source = pom(coordinate)
        root = ET.fromstring(data)
        licenses = []
        for _ in range(8):
            licenses = [{'name': item.findtext('p:name', '', NS), 'url': item.findtext('p:url', '', NS)}
                        for item in root.findall('p:licenses/p:license', NS)]
            if licenses:
                break
            parent = root.find('p:parent', NS)
            if parent is None:
                break
            parent_coordinate = tuple(parent.findtext('p:' + key, '', NS) for key in ['groupId', 'artifactId', 'version'])
            if any(not value or '${' in value for value in parent_coordinate):
                break
            parent_data, metadata_source = pom(parent_coordinate)
            root = ET.fromstring(parent_data)
        if not licenses:
            raise RuntimeError(f'License declaration unresolved: {package}')
        for license in licenses:
            if 'apache' in license['name'].lower() and '2' in license['name']:
                license['spdx'] = 'Apache-2.0'
            elif 'mit' in license['name'].lower():
                license['spdx'] = 'MIT'
            else:
                raise RuntimeError(f'License requires review: {package}: {licenses}')
        notices = []
        artifacts = []
        for artifact_file in sorted((cache / group / artifact / package['version']).glob('*/*')):
            if artifact_file.suffix in ['.jar', '.aar'] and not artifact_file.name.endswith(('-sources.jar', '-javadoc.jar')):
                artifacts.append(artifact_file.name)
                notices.extend(archive_notices(artifact_file))
        return dict(**package, licenses=licenses, licenseMetadata=metadata_source, artifacts=artifacts, notices=notices)

    with ThreadPoolExecutor(max_workers=6) as pool:
        resolved = list(pool.map(maven_package, packages))
    lock_path = Path('native/code-reader/Cargo.lock')
    lock = tomllib.loads(lock_path.read_text(encoding='utf-8'))
    rust = []
    for package in lock['package']:
        if not package.get('source', '').startswith('registry+'):
            continue
        directories = list(args.cargo_registry.glob(f"*/{package['name']}-{package['version']}"))
        if len(directories) != 1:
            raise RuntimeError(f'Expected one unpacked Cargo source for {package["name"]}')
        folder = directories[0]
        manifest = tomllib.loads((folder / 'Cargo.toml').read_text(encoding='utf-8'))['package']
        reviewed = {'MIT OR Apache-2.0', 'MIT', 'Unlicense OR MIT', 'Unlicense/MIT',
                    'Apache-2.0/MIT', 'MIT/Apache-2.0', '(MIT OR Apache-2.0) AND Unicode-3.0'}
        if manifest.get('license') not in reviewed:
            raise RuntimeError(f'Cargo license requires review: {package["name"]}: {manifest.get("license")}')
        notices = [{'source': p.name, 'text': text_blob(p.read_text(encoding='utf-8'))}
                   for p in sorted(folder.iterdir()) if p.is_file() and re.match(r'^(LICENSE|LICENCE|NOTICE|COPYING|COPYRIGHT)(?:[._-]|$)', p.name, re.I)]
        if package['name'] == 'jni-sys-macros' and package['version'] == '0.4.1':
            notices.append({'source': 'https://github.com/jni-rs/jni-sys/blob/64d77b7a5f119d7b55b4e2c169a4668067ff59e6/LICENSE-MIT',
                            'text': text_blob(Path('docs/legal/licenses/jni-sys-MIT.txt').read_text(encoding='utf-8'))})
        if not notices or not manifest.get('license'):
            raise RuntimeError(f'Unresolved Cargo license: {package["name"]}')
        rust.append({'ecosystem': 'crates.io', 'name': package['name'], 'version': package['version'],
                     'licenseExpression': manifest['license'], 'notices': notices})
    ledger = {'scope': 'Resolved release runtime Maven graph (including metadata/platforms) and Cargo.lock registry superset (including build/target-specific crates). Font and project licenses are documented separately.',
              'cargoLockSha256': hashlib.sha256(lock_path.read_text(encoding='utf-8').encode('utf-8')).hexdigest(),
              'maven': resolved, 'rust': rust}
    Path('docs/legal/dependencies.json').write_text(json.dumps(ledger, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    paragraphs = ['# Resolved third-party dependencies', ledger['scope'],
                  'License declarations below come from published package metadata. Complete license and notice texts follow this inventory in the offline application notice.']
    for ecosystem, entries in [('Maven', resolved), ('Rust', rust)]:
        paragraphs.append(f'## {ecosystem} dependencies')
        for item in entries:
            declaration = ', '.join(license['spdx'] for license in item['licenses']) if ecosystem == 'Maven' else item['licenseExpression']
            paragraphs.append(f"- `{item['name']}:{item['version']}` — {declaration}")
    Path('docs/legal/dependencies.md').write_text('\n\n'.join(paragraphs) + '\n', encoding='utf-8')
    print(f'Inventoried {len(resolved)} Maven modules and {len(rust)} Cargo packages; {len(list(destination.glob("*.txt")))} deduplicated notice texts.')


if __name__ == '__main__':
    main()
