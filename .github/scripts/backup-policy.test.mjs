import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../../', import.meta.url));

test('backup and device transfer exclude device bindings and keep legacy restore cleanup enabled', () => {
  const result = spawnSync('python3', ['-c', `
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
root = Path(sys.argv[1])
expected = {'app_state.xml', 'settings.xml', 'scheduled_alarms.xml', 'snoozed_alarms.xml'}
for name in ('backup_rules', 'data_extraction_rules'):
    tree = ET.parse(root / f'app/src/main/res/xml/{name}.xml').getroot()
    groups = [tree] if name == 'backup_rules' else list(tree)
    if name == 'data_extraction_rules':
        assert {group.tag for group in groups} >= {'cloud-backup', 'device-transfer'}
    for group in groups:
        excluded = {item.get('path') for item in group.findall('exclude') if item.get('domain') == 'sharedpref'}
        assert expected <= excluded, (name, group.tag, expected - excluded)
app = ET.parse(root / 'app/src/main/AndroidManifest.xml').getroot().find('application')
ns = '{http://schemas.android.com/apk/res/android}'
assert app.get(ns + 'backupAgent') == '.backup.SlowClockBackupAgent'
assert app.get(ns + 'fullBackupOnly') == 'true'
`, root], { encoding: 'utf8' });
  assert.equal(result.status, 0, result.stderr);
});
