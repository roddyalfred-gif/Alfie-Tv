import { cpSync, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { resolve, join } from 'node:path';

const root = resolve(process.cwd());
const webDist = join(root, 'packages/web/dist');
const out = join(root, 'dist/smart-tv');
const tizenDir = join(out, 'tizen');
const webosDir = join(out, 'webos');

if (!existsSync(webDist)) {
  throw new Error('packages/web/dist is missing. Run npm run build -w @alfie-tv/web first.');
}

rmSync(out, { recursive: true, force: true });
mkdirSync(out, { recursive: true });

const prepare = (dir) => {
  cpSync(webDist, dir, { recursive: true });
  const indexPath = join(dir, 'index.html');
  const html = readFileSync(indexPath, 'utf8');
  const navigation = '<script src="platform-navigation.js"></script>\n';
  writeFileSync(indexPath, html.includes('platform-navigation.js') ? html : html.replace('<body>', `<body>\n  ${navigation}`));
};

prepare(tizenDir);
prepare(webosDir);

const tizenConfig = readFileSync(join(root, 'apps/tizen/config.xml'), 'utf8');
writeFileSync(join(tizenDir, 'config.xml'), tizenConfig);
cpSync(join(root, 'apps/tizen/platform-navigation.js'), join(tizenDir, 'platform-navigation.js'));

const webosInfo = JSON.parse(readFileSync(join(root, 'apps/webos/appinfo.json'), 'utf8'));
writeFileSync(join(webosDir, 'appinfo.json'), JSON.stringify(webosInfo, null, 2));
cpSync(join(root, 'apps/webos/platform-navigation.js'), join(webosDir, 'platform-navigation.js'));

const zip = (source, target) => execFileSync('zip', ['-qr', target, '.'], { cwd: source, stdio: 'inherit' });
zip(tizenDir, join(out, 'alfie-tv-tizen-unsigned.wgt'));

// webOS .ipk files are ar archives containing debian-binary, control.tar.gz and data.tar.gz.
const control = join(out, 'webos-control');
const data = join(out, 'webos-data');
rmSync(control, { recursive: true, force: true });
rmSync(data, { recursive: true, force: true });
mkdirSync(control, { recursive: true });
mkdirSync(data, { recursive: true });
cpSync(webosDir, join(data, 'com.alfietv.player'), { recursive: true });
writeFileSync(join(control, 'control'), [
  'Package: com.alfietv.player',
  'Version: 1.0.0',
  'Architecture: all',
  'Section: misc',
  'Priority: optional',
  'Maintainer: Alfie TV',
  'Description: Alfie TV IPTV player for LG webOS TV',
  ''
].join('\n'));
writeFileSync(join(out, 'debian-binary'), '2.0\n');
execFileSync('tar', ['-czf', join(out, 'control.tar.gz'), '-C', control, 'control']);
execFileSync('tar', ['-czf', join(out, 'data.tar.gz'), '-C', data, 'com.alfietv.player']);
execFileSync('ar', ['r', join(out, 'alfie-tv-webos-unsigned.ipk'), join(out, 'debian-binary'), join(out, 'control.tar.gz'), join(out, 'data.tar.gz')]);

rmSync(control, { recursive: true, force: true });
rmSync(data, { recursive: true, force: true });
rmSync(join(out, 'debian-binary'));
rmSync(join(out, 'control.tar.gz'));
rmSync(join(out, 'data.tar.gz'));

console.log('Smart TV packages created:');
console.log(join(out, 'alfie-tv-tizen-unsigned.wgt'));
console.log(join(out, 'alfie-tv-webos-unsigned.ipk'));
