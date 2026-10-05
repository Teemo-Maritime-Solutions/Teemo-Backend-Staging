"""Bounded public index discovery; no credentials, archives, imports or TLS bypass."""
import argparse
from datetime import datetime, timezone
import hashlib
from html.parser import HTMLParser
import json
from pathlib import Path
import urllib.error
import urllib.parse
import urllib.request


SOURCES = {
    'dma-ais-index': 'https://aisdata.ais.dk/',
    'linz-enc-download': 'https://encservice.linz.govt.nz/download',
}
MAX_BYTES = 2_000_000


class Links(HTMLParser):
    def __init__(self, base):
        super().__init__()
        self.base, self.links = base, []

    def handle_starttag(self, tag, attrs):
        href = dict(attrs).get('href')
        if tag == 'a' and href and len(self.links) < 100:
            url = urllib.parse.urljoin(self.base, href)
            if urllib.parse.urlsplit(url).scheme == 'https':
                self.links.append(url)


class SameHostRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        before, after = urllib.parse.urlsplit(req.full_url), urllib.parse.urlsplit(newurl)
        if after.scheme != 'https' or after.hostname != before.hostname:
            raise ValueError('Cross-host or non-TLS redirect needs separate review')
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def probe(source):
    url = SOURCES[source]
    result = dict(sourceId=source, url=url, checkedAt=datetime.now(timezone.utc).isoformat(),
                  scope='PUBLIC_INDEX_ONLY_NOT_DATA_OR_LICENCE_VERIFICATION')
    try:
        request = urllib.request.Request(url, headers={'User-Agent': 'MaritimeResearchSourceAudit/1.0'})
        with urllib.request.build_opener(SameHostRedirects()).open(request, timeout=20) as response:
            if response.headers.get_content_type() not in ('text/html', 'text/plain'):
                raise ValueError('Not a public text index')
            payload = response.read(MAX_BYTES + 1)
            if len(payload) > MAX_BYTES:
                raise ValueError('Public index exceeds read bound')
            parser = Links(response.url)
            parser.feed(payload.decode(response.headers.get_content_charset() or 'utf-8', errors='replace'))
            result.update(status='INDEX_READ', httpStatus=response.status, finalUrl=response.url,
                          bytes=len(payload), sha256=hashlib.sha256(payload).hexdigest(), links=parser.links)
    except urllib.error.HTTPError as error:
        result.update(status='HTTP_ERROR', httpStatus=error.code)
    except (urllib.error.URLError, TimeoutError, OSError, ValueError) as error:
        result.update(status='ACCESS_ERROR', errorType=type(error).__name__, detail=str(error))
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', choices=SOURCES, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise ValueError('Immutable audit already exists')
    result = probe(args.source)
    result['implementationSha256'] = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    # Sandbox denials must be retried through approval, not recorded as public unavailability.
    if '10013' in result.get('detail', ''):
        raise PermissionError(result['detail'])
    with args.output.open('x', encoding='utf-8') as stream:
        json.dump(result, stream, sort_keys=True, indent=2)
    print(json.dumps(result), flush=True)
