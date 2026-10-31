#!/usr/bin/env python3
"""Disable the original KO's legacy bootstrap/log command, preserving ELF layout.

The Arashi assembly worker owns bootstrap execution after insmod returns.
Original KO files still reference df.root; those paths must not be executed.
"""
import pathlib
import sys

PREFIX = b'touch /dev/dfm0; mkdir -p /data/media/0/Download/DirtyFrag;'
COMMAND = b'touch /dev/dfm0;%s'


def relocate(data):
    if not data.startswith(b'\x7fELF'):
        raise ValueError('Expected an ELF module')
    strings = data.split(b'\0')
    matches = [i for i, s in enumerate(strings) if s.startswith(PREFIX)]
    if len(matches) != 1:
        raise ValueError('Expected exactly one upstream UMH command')
    index = matches[0]
    if not strings[index].endswith(b'%s exec %s'):
        raise ValueError('Unknown upstream command format')
    strings[index] = COMMAND.ljust(len(strings[index]), b'\0')
    out = b'\0'.join(strings)
    if len(out) != len(data):
        raise ValueError('Invalid relocation')
    return out


if __name__ == '__main__':
    source, target = map(pathlib.Path, sys.argv[1:])
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(relocate(source.read_bytes()))
