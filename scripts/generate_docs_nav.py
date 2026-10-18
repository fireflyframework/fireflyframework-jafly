#!/usr/bin/env python3
"""Keep the reference navigation aligned with Jafly's eight website capabilities."""
import argparse
import json
from pathlib import Path
import re
import sys

CAPABILITIES = [
    ('security-and-identity', 'Security and identity', 'Map principals to policy decisions with security contracts, a reactive resource server, method guards, and provider adapters.'),
    ('business-operations', 'Business operations', 'Dispatch commands and queries through validation, authorization, handlers, and optional event and cache bridges.'),
    ('external-services', 'External services', 'Connect HTTP, SOAP, gRPC, notifications, callbacks, and Agentic through explicit service and provider integrations.'),
    ('event-driven-integration', 'Event-driven integration', 'Use event contracts and transport adapters for in-process events, Kafka, RabbitMQ, PostgreSQL, and inbound webhooks.'),
    ('domain-and-data', 'Domain and data', 'Choose reactive data helpers, optional event history, cache providers, content storage, and signature adapters.'),
    ('business-coordination', 'Business coordination', 'Coordinate workflows, compensating sagas, and try-confirm-cancel steps with explicit recovery and failure policies.'),
    ('service-operations', 'Service operations', 'Connect application telemetry, HTTP conventions, health checks, and centralized configuration.'),
    ('team-wide-architecture', 'Team-wide architecture', 'Share a dependency train, build conventions, service-layer starters, common types, validation, and extension contracts.'),
]


def category(slug):
    if slug.startswith('security-'):
        return 0
    if slug == 'cqrs' or slug.startswith('rule-engine'):
        return 1
    if slug.startswith(('client', 'notifications', 'callbacks', 'agentic-bridge')):
        return 2
    if slug.startswith(('eda', 'webhooks')):
        return 3
    if slug.startswith(('cache', 'ecm')) or slug in ('r2dbc', 'eventsourcing'):
        return 4
    if slug == 'orchestration':
        return 5
    if slug in ('web', 'observability', 'config-server'):
        return 6
    return 7


LEAD_MODULES = [
    'security-api', 'security-spi', 'security-core', 'security-webflux',
    'security-resource-server', 'security-method-policy', 'security-oauth2-client',
    'security-authorization-server', 'security-bff', 'security-idp', 'security-test',
    'cqrs', 'rule-engine', 'rule-engine-interfaces', 'rule-engine-models', 'rule-engine-core',
    'client', 'client-complete-example', 'notifications', 'notifications-core', 'callbacks',
    'agentic-bridge', 'agentic-bridge-core', 'agentic-bridge-autoconfigure', 'agentic-bridge-starter',
    'eda', 'eda-kafka', 'eda-rabbitmq', 'eda-postgres', 'webhooks',
    'r2dbc', 'eventsourcing', 'cache', 'ecm', 'orchestration', 'observability', 'web', 'config-server',
    'parent', 'bom', 'kernel', 'utils', 'validators', 'starter-core', 'starter-domain',
    'starter-application', 'starter-data', 'backoffice', 'plugins', 'plugin-api', 'plugin-core',
]


def sentence_case(title):
    brands = {word.lower(): word for word in (
        'API SPI WebFlux OAuth2 BFF IDP AWS Cognito Azure AD Keycloak Cerbos OPA '
        'OpenFGA R2DBC Vault CQRS SDK Agentic Firebase Resend SendGrid Twilio EDA '
        'Kafka RabbitMQ PostgreSQL JCache Hazelcast Redis ECM S3 Blob Adobe Sign '
        'DocuSign Logalty Java BOM eSignature'
    ).split()}
    words = title.replace(' - ', ': ').split()
    return ' '.join(brands.get(word.rstrip(':').lower(),
                              word.rstrip(':') if index == 0 else word.rstrip(':').lower()) +
                    (':' if word.endswith(':') else '')
                    for index, word in enumerate(words))


def generated(root):
    catalog = json.loads((root / 'docs/site/assets/module-catalog.json').read_text())
    catalog.sort(key=lambda item: (category(item['slug']), LEAD_MODULES.index(item['slug']) if item['slug'] in LEAD_MODULES else len(LEAD_MODULES), item['title']))
    capabilities = [{'id': key, 'title': title, 'summary': summary,
                     'modules': [item['slug'] for item in catalog if category(item['slug']) == index]}
                    for index, (key, title, summary) in enumerate(CAPABILITIES)]
    lines = ['  - Modules:', '      - Catalog: modules/index.md']
    for capability in capabilities:
        lines.append('      - ' + json.dumps(capability['title']) + ':')
        for item in catalog:
            if item['slug'] not in capability['modules']:
                continue
            label = sentence_case(item['title'].removeprefix('Common ').removeprefix(': ').removesuffix(' Library'))
            lines.append('          - ' + json.dumps(label) + ': modules/' + item['slug'] + '.md')
    return '\n'.join(lines) + '\n', json.dumps(capabilities, indent=2) + '\n'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    config = root / 'mkdocs.yml'
    current = config.read_text()
    navigation, capabilities = generated(root)
    updated, count = re.subn(r'  - Modules:\n.*?(?=  - Contribute:)', lambda _: navigation, current, flags=re.S)
    if count != 1:
        raise ValueError('MkDocs Modules navigation boundary is missing or ambiguous')
    data = root / 'docs/site/assets/capabilities.json'
    if args.check:
        if current != updated or not data.exists() or data.read_text() != capabilities:
            raise ValueError('generated capability navigation is stale; run scripts/generate_docs_nav.py')
    else:
        config.write_text(updated)
        data.write_text(capabilities)
    print('Validated eight capability sections and all module navigation links')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError) as error:
        print(f'ERROR: {error}', file=sys.stderr)
        sys.exit(1)
