"""J4A host-only output/interface oracle; no real GITHUB_OUTPUT or live service use."""
import contextlib
import copy
import io
import json
import os
from pathlib import Path
import subprocess
import sys
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'publishing'))
import release
import publication

CP, destination = sys.argv[1:]
OUT = Path(destination)
OUT.mkdir(parents=True, exist_ok=True)
JAVA = str(Path(os.environ['JAVA_HOME']) / 'bin/java')
ENV = dict(REQUESTED_VERSION='0.2.0', REQUESTED_COMMIT='a' * 40, DISPATCH_REF='refs/heads/main', DISPATCH_SHA='a' * 40)
results = []


def run(*args, env=ENV):
    return subprocess.run([JAVA, '-cp', CP, 'org.totipo.tooling.release.WorkflowOutputFixture', *map(str, args)], env=env, capture_output=True)


def compare(name, command, python, env=ENV):
    output = OUT / 'java-output'
    output.write_bytes(b'')
    result = run(*command, output, env=env)
    python_output = OUT / 'python-output'
    python_output.write_bytes(b'')
    with patch.dict(os.environ, dict(env, GITHUB_OUTPUT=str(python_output)), clear=True), contextlib.redirect_stdout(io.StringIO()):
        try:
            python(python_output)
            accepted = True
        except Exception:
            accepted = False
    assert accepted == (result.returncode == 0), (name, result.stderr)
    assert output.read_bytes() == python_output.read_bytes(), name
    if not accepted:
        assert not output.read_bytes(), name
    results.append(dict(case=name, accepted=accepted, bytes=output.read_text()))


def inputs(output):
    version, commit = release.inputs()
    output.write_text(f'version={version}\ncommit={commit}\n')


compare('inputs-valid', ['inputs'], inputs)
for key, value in [('REQUESTED_VERSION', '0.2.0\nmode=publish'), ('REQUESTED_VERSION', '0.2.0-SNAPSHOT'),
                   ('REQUESTED_COMMIT', 'A' * 40), ('REQUESTED_COMMIT', 'a' * 39), ('DISPATCH_REF', 'refs/heads/other'), ('DISPATCH_SHA', 'b' * 40)]:
    compare('inputs:' + key + ':' + repr(value), ['inputs'], inputs, dict(ENV, **{key: value}))
root = OUT / 'source'
root.mkdir()
subprocess.run([JAVA, '-cp', CP, 'org.totipo.tooling.release.AuthorizationFixture', 'create', str(root)], check=True)
with patch.object(release, 'ROOT', root):
    def python_inventory(output):
        with patch.object(sys, 'argv', ['release.py', 'inventory']):
            release.main()
    compare('inventory-exact-compact-json', ['inventory', root], python_inventory)
    for core in (200, 404):
        for storage in (200, 404):
            for prior in (True, False):
                def python_state(output):
                    with patch.object(release, 'prior_upload', return_value=prior), patch.object(release, 'request', side_effect=[(core, b''), (storage, b'')]), patch.object(sys, 'argv', ['release.py', 'state']):
                        release.main()
                compare(f'state:{core}:{storage}:{prior}', ['state', str(core), str(storage), str(prior).lower()], python_state)
    manifest = json.loads((root / 'build/publication-manifest.json').read_text())
    expected = OUT / 'expected.json'
    for name in ('equal', 'fingerprint-only', 'hash', 'version', 'malformed'):
        candidate = copy.deepcopy(manifest)
        if name == 'fingerprint-only': candidate['repositorySha256'] = 'f' * 64
        if name == 'hash': candidate['artifacts'][0]['sha256'] = 'f' * 64
        if name == 'version': candidate['version'] = '0.2.1'
        text = '{}' if name == 'malformed' else json.dumps(candidate)
        expected.write_text(text)
        java_result = run('compare', root, expected).returncode == 0
        with patch.dict(os.environ, dict(ENV, EXPECTED_INVENTORY=text), clear=True), contextlib.redirect_stdout(io.StringIO()):
            try: release.compare_inventory(); python_result = True
            except Exception: python_result = False
        assert java_result == python_result, name
        results.append(dict(case='compare:' + name, accepted=java_result))
OUT.joinpath('results.json').write_text(json.dumps(results, indent=2) + '\n')
print(f'Workflow output/interface parity PASS: {len(results)} cases; exact output bytes; zero unexplained drift')
pins = OUT / 'pins/.github/workflows/test.yml'
pins.parent.mkdir(parents=True)
valid = ('uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1\n'
         'uses: actions/setup-java@de7274f081f381c8f8158605e0321c36c376e2e6 # v6.0.1\n'
         'uses: cachix/install-nix-action@13d8dd58da0234aa297dedd986986ccb8e7f3e24 # v31.11.1\n')
for suffix in ('', 'uses: actions/checkout@v7', 'uses: docker://unknown/image:latest', 'uses: ./local',
               "'uses': actions/checkout@v7", '{uses: "actions/checkout@v7"}',
               'uses: other/action@' + 'a' * 40 + ' # v7.0.1'):
    pins.write_text(valid + suffix)
    java_result = run('pins', OUT / 'pins').returncode == 0
    try:
        from test_release import WorkflowPinTests
        found = WorkflowPinTests().reviewed_uses(pins.read_text())
        assert set(found) == set(WorkflowPinTests.APPROVED)
        python_result = True
    except AssertionError:
        python_result = False
    assert java_result == python_result, suffix
    results.append(dict(case='workflow-pins:' + repr(suffix), accepted=java_result))
OUT.joinpath('results.json').write_text(json.dumps(results, indent=2) + '\n')
print(f'Including action-pin guard: {len(results)} output/interface cases; zero unexplained drift')
