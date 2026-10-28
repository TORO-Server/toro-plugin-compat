"""Build the Paper 26.3 compatible Vehicles jar.

Copies every entry of the original jar unchanged (same names, order, timestamps, compression)
and replaces only the classes that were recompiled from the compatibility sources.
usage: python -I build_jar.py <original.jar> <classes_dir> <output.jar>
"""
import os, sys, zipfile

orig, classes_dir, out = sys.argv[1:4]
replacements = {}
for root, _, files in os.walk(classes_dir):
    for f in files:
        if f.endswith('.class'):
            full = os.path.join(root, f)
            replacements[os.path.relpath(full, classes_dir).replace(os.sep, '/')] = full

with zipfile.ZipFile(orig) as zin, zipfile.ZipFile(out, 'w') as zout:
    seen = set()
    for info in zin.infolist():
        data = zin.read(info.filename)
        if info.filename in replacements:
            with open(replacements[info.filename], 'rb') as fh:
                data = fh.read()
            seen.add(info.filename)
        new = zipfile.ZipInfo(info.filename, info.date_time)
        new.compress_type = info.compress_type
        new.external_attr = info.external_attr
        new.create_system = info.create_system
        zout.writestr(new, data)
    for name, path in replacements.items():
        if name not in seen:  # new classes: reuse the timestamp of the first existing entry
            first = zin.infolist()[0]
            new = zipfile.ZipInfo(name, first.date_time)
            new.compress_type = zipfile.ZIP_DEFLATED
            with open(path, 'rb') as fh:
                zout.writestr(new, fh.read())
print('replaced:', sorted(seen), 'added:', sorted(set(replacements) - seen))
