#!/usr/bin/env python3
"""Check that the published generator property and resolved gRPC runtime agree."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--maven', default='mvn')
    parser.add_argument('--settings', type=Path)
    parser.add_argument('--repository', type=Path)
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    parent = Path(__file__).resolve().parents[1] / 'build/parent/pom.xml'
    namespace = {'m': 'http://maven.apache.org/POM/4.0.0'}
    version = ET.parse(parent).findtext('m:version', namespaces=namespace)
    with tempfile.TemporaryDirectory(prefix='jafly-grpc-alignment-') as temporary:
        root = Path(temporary).resolve()
        dependencies = ''.join(
            '<dependency><groupId>io.grpc</groupId><artifactId>' + artifact
            + '</artifactId><version>${grpc.version}</version></dependency>'
            for artifact in ('grpc-netty-shaded', 'grpc-protobuf', 'grpc-stub'))
        (root / 'pom.xml').write_text(
            '<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>'
            '<parent><groupId>org.fireflyframework</groupId>'
            '<artifactId>fireflyframework-parent</artifactId><version>' + escape(version)
            + '</version><relativePath>' + escape(os.path.relpath(parent, root)) + '</relativePath></parent>'
            '<groupId>org.fireflyframework.qualification</groupId>'
            '<artifactId>grpc-alignment-probe</artifactId><version>1</version>'
            '<dependencies>' + dependencies + '</dependencies></project>')
        command = [args.maven, '-B', '-ntp']
        if args.settings:
            command.extend(['-s', str(args.settings.resolve())])
        if args.repository:
            command.append('-Dmaven.repo.local=' + str(args.repository.resolve()))
        command.extend(['help:effective-pom', '-Doutput=' + str(root / 'effective.xml'),
                        'dependency:tree', '-Dincludes=io.grpc',
                        '-DoutputFile=' + str(root / 'tree.txt')])
        process = subprocess.run(command, cwd=root, capture_output=True, text=True, timeout=180)
        if process.returncode:
            raise SystemExit(process.stdout + process.stderr)
        effective = ET.parse(root / 'effective.xml')
        expected = effective.findtext('m:properties/m:grpc.version', namespaces=namespace)
        tree = (root / 'tree.txt').read_text()
        resolved = sorted(set(re.findall(r'io\.grpc:([^:\s]+):jar:([^:\s]+):', tree)))
        mismatches = [{'artifact': artifact, 'version': selected}
                      for artifact, selected in resolved if selected != expected]
        report = {'status': 'PASS' if resolved and not mismatches else 'FAIL',
                  'framework_version': version, 'grpc_generator_version': expected,
                  'resolved': [dict(artifact=a, version=v) for a, v in resolved],
                  'mismatches': mismatches, 'dependency_tree': tree,
                  'scope': 'Actual Maven consumer graph; native compiler and transport need runtime qualification.'}
        if args.report:
            args.report.parent.mkdir(parents=True, exist_ok=True)
            args.report.write_text(json.dumps(report, indent=2) + '\n')
        print(json.dumps(report, indent=2))
        return report['status'] != 'PASS'


if __name__ == '__main__':
    raise SystemExit(main())
