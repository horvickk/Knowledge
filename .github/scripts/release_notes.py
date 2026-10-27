#!/usr/bin/env python3
"""Описание релиза: что изменилось с предыдущей сборки этой ветки и данные сборки.

Раздел «## Что нового» приложение показывает в окне обновления (см. Releases.notes), поэтому
формат простой: «### заголовок коммита», под ним пункты «- » из описания коммита.

Переменные окружения: RUN (номер сборки), BRANCH, SHA, CERT (отпечаток ключа подписи).
Текст печатается в stdout.
"""
import os
import re
import subprocess

TRAILER = re.compile(r'^(Co-Authored-By|Claude-Session|Signed-off-by):', re.I)
MAX_COMMITS = 30


def git(*args):
    return subprocess.run(['git', *args], capture_output=True, text=True, check=True).stdout


def build_number(tag):
    tail = tag[len('build-'):]
    return int(tail) if tail.isdigit() else -1


def previous_tag(current):
    """Последняя сборка, чей коммит входит в историю текущего: сборки других веток не в счёт."""
    tags = [t for t in git('tag', '-l', 'build-*').split() if t != current and build_number(t) >= 0]
    for tag in sorted(tags, key=build_number, reverse=True):
        if subprocess.run(['git', 'merge-base', '--is-ancestor', tag, 'HEAD']).returncode == 0:
            return tag
    return None


def commit_notes(message):
    lines = [l.rstrip() for l in message.strip().splitlines() if not TRAILER.match(l)]
    if not lines:
        return None
    title, items = lines[0].strip(), []
    paragraph = False
    for line in lines[1:]:
        if not line.strip():
            paragraph = False
        elif line.startswith('- '):
            items.append(line[2:].strip())
            paragraph = False
        elif line.startswith(' ') and items:
            items[-1] += ' ' + line.strip()
        elif paragraph and items:
            items[-1] += ' ' + line.strip()
        else:
            items.append(line.strip())
            paragraph = True
    return title, items


def main():
    run = os.environ.get('RUN', '')
    prev = previous_tag(f'build-{run}')
    revs = f'{prev}..HEAD' if prev else 'HEAD'
    raw = git('log', '--no-merges', f'-n{MAX_COMMITS}', '--format=%B%x00', revs)
    commits = [c for c in (commit_notes(m) for m in raw.split('\0')) if c]

    out = ['## Что нового', '']
    if commits:
        for title, items in commits:
            out.append(f'### {title}')
            out += [f'- {i}' for i in items]
            out.append('')
    else:
        out += ['Пересборка без изменений в коде.', '']

    out += [
        '## Сборка',
        '',
        f"Ветка `{os.environ.get('BRANCH', '')}`, коммит {os.environ.get('SHA', '')}"
        + (f', изменения с {prev}.' if prev else '.'),
        'Скачайте APK ниже и установите на Android 8.0+ — или обновитесь из самого приложения.',
        '',
        f"Отпечаток ключа подписи (SHA-256): `{os.environ.get('CERT', '')}`",
        'Сборки с одинаковым отпечатком ставятся поверх друг друга без потери прогресса.',
    ]
    print('\n'.join(out))


if __name__ == '__main__':
    main()
