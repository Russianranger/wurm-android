#!/usr/bin/env python3
"""Compare a privately supplied game's LWJGL references with candidate provider JARs.

Reads bounded class metadata only. Does not initialize classes, execute the game,
copy proprietary code or fall back to LWJGL classes bundled inside the game JAR.
"""
import argparse
from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
import re
import zipfile

LIMIT = 4 * 1024 * 1024


class Reader:
    def __init__(self, data):
        if len(data) > LIMIT:
            raise ValueError('Class exceeds 4 MiB metadata limit')
        self.data, self.offset = data, 0

    def take(self, size):
        if size < 0 or self.offset + size > len(self.data):
            raise ValueError('Truncated class file')
        value = self.data[self.offset:self.offset + size]
        self.offset += size
        return value

    def number(self, size):
        return int.from_bytes(self.take(size), 'big')


@dataclass(frozen=True, order=True)
class Reference:
    kind: str
    owner: str
    name: str
    descriptor: str


@dataclass
class ClassInfo:
    name: str
    parents: tuple
    members: dict
    references: set
    classes: set


def parse_class(data):
    r = Reader(data)
    if r.number(4) != 0xCAFEBABE:
        raise ValueError('Not a class file')
    r.take(4)  # Version is not a linkage guarantee; no bytecode is executed here.
    pool = [None] * r.number(2)
    index = 1
    while index < len(pool):
        tag = r.number(1)
        if tag == 1:
            # Constant strings can contain modified UTF-8; only API identifiers are used.
            value = r.take(r.number(2)).decode('utf-8', errors='replace')
        elif tag in (3, 4):
            value = r.take(4)
        elif tag in (5, 6):
            value = r.take(8)
        elif tag in (7, 8, 16, 19, 20):
            value = r.number(2)
        elif tag in (9, 10, 11, 12, 17, 18):
            value = (r.number(2), r.number(2))
        elif tag == 15:
            value = (r.number(1), r.number(2))
        else:
            raise ValueError(f'Unknown constant-pool tag {tag}')
        pool[index] = (tag, value)
        index += 2 if tag in (5, 6) else 1

    def item(i, tag):
        if not 0 < i < len(pool) or pool[i] is None or pool[i][0] != tag:
            raise ValueError('Invalid constant-pool reference')
        return pool[i][1]

    def utf(i):
        return item(i, 1)

    def classname(i):
        return utf(item(i, 7))

    r.take(2)
    name, parent = classname(r.number(2)), r.number(2)
    parents = ([classname(parent)] if parent else []) + [classname(r.number(2)) for _ in range(r.number(2))]

    def attributes():
        for _ in range(r.number(2)):
            utf(r.number(2))
            r.take(r.number(4))

    members, descriptors = {}, []
    for kind in ('field', 'method'):
        for _ in range(r.number(2)):
            flags, member, descriptor = r.number(2), utf(r.number(2)), utf(r.number(2))
            members[(kind, member, descriptor)] = flags
            descriptors.append(descriptor)
            attributes()
    attributes()
    if r.offset != len(data):
        raise ValueError('Trailing class bytes')
    references, classes = set(), set()
    for entry in pool:
        if entry is None:
            continue
        tag, value = entry
        if tag == 7:
            cls = utf(value)
            if not cls.startswith('['):
                classes.add(cls)
            else:
                descriptors.append(cls)
        if tag in (9, 10, 11):
            owner, signature = value
            member, descriptor = item(signature, 12)
            references.add(Reference('field' if tag == 9 else 'method', classname(owner), utf(member), utf(descriptor)))
        if tag == 12:
            descriptors.append(utf(value[1]))
    for descriptor in descriptors:
        classes.update(re.findall(r'L([^;]+);', descriptor))
    return ClassInfo(name, tuple(parents), members, references, classes)


def read_jar(path, prefix):
    result = {}
    with zipfile.ZipFile(path) as jar:
        if len(jar.infolist()) > 100000:
            raise ValueError('JAR exceeds 100000 entries')
        for entry in jar.infolist():
            if not entry.filename.startswith(prefix) or not entry.filename.endswith('.class'):
                continue
            if entry.file_size > LIMIT:
                raise ValueError('Class exceeds 4 MiB metadata limit')
            info = parse_class(jar.read(entry))
            if entry.filename != info.name + '.class':
                raise ValueError('Class path/name mismatch')
            if info.name in result:
                raise ValueError(f'Duplicate class in JAR: {info.name}')
            result[info.name] = info
    return result


def lookup(reference, providers):
    """Return found/missing/unknown; never assume an absent parent provides a member."""
    visited, unknown = set(), False
    pending = [reference.owner]
    while pending:
        owner = pending.pop()
        if owner in visited:
            continue
        visited.add(owner)
        info = providers.get(owner)
        if info is None:
            # Object's member set is not supplied; missing inherited methods stay unresolved.
            unknown = True
            continue
        flags = info.members.get((reference.kind, reference.name, reference.descriptor))
        if flags is not None and flags & (0x0001 | 0x0004):
            return 'found'
        if reference.name != '<init>':
            pending.extend(info.parents)
    return 'unknown' if unknown else 'missing'


def audit(client, adapters, caller_prefix='com/wurmonline/', platforms=()):
    callers = read_jar(client, caller_prefix)
    if not callers:
        raise ValueError('No caller classes matched; refusing an empty success')
    providers = {}
    for path in adapters:
        for name, info in read_jar(path, 'org/lwjgl/').items():
            if name in providers:
                raise ValueError(f'Duplicate adapter provider: {name}')
            providers[name] = info
    if not providers:
        raise ValueError('No LWJGL adapter providers')
    provider_count = len(providers)
    for path in platforms:
        for name, info in read_jar(path, 'java/').items():
            if name in providers:
                raise ValueError(f'Duplicate platform provider: {name}')
            providers[name] = info
    required, references = set(), {}
    for name, info in callers.items():
        required.update(n for n in info.classes if n.startswith('org/lwjgl/'))
        for ref in info.references:
            if ref.owner.startswith('org/lwjgl/'):
                references.setdefault(ref, set()).add(name)
                required.add(ref.owner)
    if not required:
        raise ValueError('No LWJGL references matched; refusing an empty success')
    missing_classes = sorted(required - providers.keys())
    missing_members, unresolved, matched = [], [], 0
    for ref, uses in sorted(references.items()):
        item = dict(kind=ref.kind, owner=ref.owner, name=ref.name, descriptor=ref.descriptor, callers=sorted(uses))
        if ref.owner not in providers:
            missing_members.append(item)
        else:
            result = lookup(ref, providers)
            if result == 'found':
                matched += 1
            elif result == 'unknown':
                unresolved.append(item)
            else:
                missing_members.append(item)
    def identity(path):
        digest = hashlib.sha256()
        with open(path, 'rb') as stream:
            for block in iter(lambda: stream.read(1024 * 1024), b''):
                digest.update(block)
        return dict(name=Path(path).name, sha256=digest.hexdigest())
    return dict(scope='Static name/descriptor coverage only; no native, behavior, access/static-dispatch or rendering qualification',
                client=identity(client), adapters=[identity(p) for p in adapters], platforms=[identity(p) for p in platforms], caller_classes=len(callers),
                provider_classes=provider_count, required_classes=len(required), required_members=len(references),
                matched_members=matched, missing_classes=missing_classes, missing_members=missing_members,
                unresolved_members=unresolved, candidate_complete=not (missing_classes or missing_members or unresolved))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--adapter', type=Path, action='append', required=True)
    parser.add_argument('--platform', type=Path, action='append', default=[], help='Optional JDK ancestor metadata JAR, never a runtime adapter')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    report = audit(args.client, args.adapter, platforms=args.platform)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({k: report[k] for k in ('caller_classes', 'provider_classes', 'required_classes', 'required_members', 'matched_members', 'candidate_complete')}))
    print(f'Missing classes={len(report["missing_classes"])}; missing members={len(report["missing_members"])}; unresolved members={len(report["unresolved_members"])}')
    return 0 if report['candidate_complete'] else 2


if __name__ == '__main__':
    raise SystemExit(main())
