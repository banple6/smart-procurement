import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const webRoot = resolve(here, '..');
const repoRoot = resolve(webRoot, '..');
const serverRoot = join(repoRoot, 'server');
const python = process.env.PYTHON || join(serverRoot, '.venv', 'bin', 'python');
const output = join(webRoot, 'src', 'api', 'openapi.json');

if (!existsSync(python)) {
  throw new Error(`Python runtime not found: ${python}`);
}

const code = `
import json, os
os.environ.setdefault("APP_ENV", "test")
os.environ.setdefault("APP_SECRET", "openapi-export")
from app.main import api
print(json.dumps(api.openapi(), ensure_ascii=False))
`;

const schema = execFileSync(python, ['-c', code], {
  cwd: serverRoot,
  env: { ...process.env, APP_ENV: process.env.APP_ENV || 'test', APP_SECRET: process.env.APP_SECRET || 'openapi-export' },
  encoding: 'utf8',
});

mkdirSync(dirname(output), { recursive: true });
writeFileSync(output, schema);
console.log(`OpenAPI schema exported to ${output}`);
