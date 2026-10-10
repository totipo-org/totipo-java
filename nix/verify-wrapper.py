"""Check reviewed wrapper identities without executing the wrapper or networking."""
import hashlib
from pathlib import Path
import re
import sys

jar, properties = map(Path, sys.argv[1:])
expected_jar = "238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5"
if hashlib.sha256(jar.read_bytes()).hexdigest() != expected_jar:
    raise SystemExit("Gradle wrapper JAR SHA-256 mismatch")
expected = {
    "distributionUrl": r"https\://services.gradle.org/distributions/gradle-9.8.0-bin.zip",
    "distributionSha256Sum": "bafd5ce9cfaea0fbccfdc8439a1ac42fbd4cd9c89dc9a988228d8a2639a58e6c",
}
lines = properties.read_text().splitlines()
if any(line.rstrip().endswith("\\") for line in lines if not line.lstrip().startswith(("#", "!"))):
    raise SystemExit("Unexpected continued Gradle wrapper property")
for key, value in expected.items():
    entries = [line for line in lines if re.match(rf"\s*{re.escape(key)}(?:\s|=|:|$)", line)]
    if entries != [f"{key}={value}"]:
        raise SystemExit(f"Gradle wrapper {key} mismatch or duplicate")
print("Gradle wrapper JAR, distribution URL and distribution SHA-256 verified")
