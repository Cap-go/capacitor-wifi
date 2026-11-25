import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

test('ConnectOptions exposes joinOnce with default documented', () => {
  const defs = readFileSync('src/definitions.ts', 'utf8');
  assert.match(defs, /joinOnce\?: boolean/);
  assert.match(defs, /@default false\s*\*\/\s*joinOnce\?: boolean/);
});

test('iOS connect forwards joinOnce to NEHotspotConfiguration', () => {
  const swift = readFileSync('ios/Sources/CapacitorWifiPlugin/CapacitorWifiPlugin.swift', 'utf8');
  assert.match(swift, /configuration\.joinOnce = call\.getBool\("joinOnce"\) \?\? false/);
});