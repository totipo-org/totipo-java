"""Host-only unchanged Python oracle. Permanent Java fixtures/tests require no Python."""
import contextlib
import io
import json
from pathlib import Path
import re
import subprocess
import sys
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'publishing'))
import release


def main():
    java, classpath, evidence = sys.argv[1:]
    evidence = Path(evidence)
    subprocess.run([java, '-cp', classpath, 'org.totipo.tooling.release.TestResultsTest', str(evidence)], check=True)
    tightenings = {'duplicate', 'missing-errors', 'case-count', 'embedded-failure', 'integer-sign', 'dtd', 'root', 'incomplete'}
    results = []
    for line in (evidence / 'results.tsv').read_text().splitlines():
        name, java_result, *details = line.split('\t')
        output = io.StringIO()
        with patch.object(release, 'ROOT', evidence / name), contextlib.redirect_stdout(output):
            try:
                release.test_results(name == 'focused')
                python_result = 'PASS'
                total = int(re.search(r'(\d+) JUnit tests', output.getvalue())[1])
                assert 'snapshot 3/3; profile 1/1' in output.getvalue()
                assert ('92/92 portable cases' in output.getvalue()) == (name != 'focused')
                python_failure = None
            except Exception as error:
                python_result, total = 'FAIL', None
                text = str(error)
                python_failure = ('XML' if type(error).__name__ == 'ParseError' else
                                  'ATTRIBUTES' if isinstance(error, (KeyError, ValueError)) else
                                  'INTEGRITY' if 'integrity accounting' in text else
                                  'CONFORMANCE' if 'portable-case' in text else
                                  'UNSUCCESSFUL' if 'failures/errors/skips' in text else 'MISSING')
        tightening = python_result == 'PASS' and java_result == 'FAIL'
        assert python_result == java_result or tightening and name in tightenings, (name, python_result, java_result)
        if java_result == 'PASS':
            assert total == int(details[0]) and details[1:3] == ['3', '1']
            assert (details[3] == 'true') == (name != 'focused')
        elif python_result == 'FAIL':
            assert python_failure == details[0] or details[0] == 'LIMIT' or name == 'empty' and details[0] == 'MISSING', (name, python_failure, details)
        results.append(dict(case=name, python=python_result, java=java_result, pythonFailure=python_failure,
                            javaDetails=details, tightening=tightening))
    evidence.joinpath('parity.json').write_text(json.dumps(results, indent=2) + '\n')
    print(f'Accounting parity PASS: {len(results)} cases; {sum(r["tightening"] for r in results)} documented tightenings; zero unexplained drift')


if __name__ == '__main__':
    main()
