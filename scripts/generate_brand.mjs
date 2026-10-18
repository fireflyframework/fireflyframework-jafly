/** Export Jafly's official outlined identity from the canonical brand kit. */
import { readFile, writeFile, mkdir, copyFile } from 'node:fs/promises';
import { resolve, join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';

const kit = resolve(process.argv[2] || '../brand-kit');
const root = resolve(new URL('..', import.meta.url).pathname);
const output = join(root, 'docs/site/assets/brand');
process.chdir(kit);
const { familyLogo, familyBanner, familyWordmark, frameworkBrands } =
  await import(pathToFileURL(join(kit, 'scripts/family-identity.mjs')));
const { roundModule } = await import(pathToFileURL(join(kit, 'scripts/identity.mjs')));
const brand = frameworkBrands.find(item => item.id === 'java');
await mkdir(output, { recursive: true });
for (const theme of ['dark', 'light']) {
  await writeFile(join(output, `jafly-logo-${theme}.svg`), familyLogo(brand, theme));
  await writeFile(join(output, `jafly-banner-${theme}.svg`), familyBanner(brand, theme));
  const wordmark = familyWordmark('jafly', 20, 130, 140, theme === 'dark' ? '#f3f1eb' : '#10110f');
  await writeFile(join(output, `jafly-wordmark-${theme}.svg`),
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${wordmark.width + 40} 180"><title>jafly</title>${wordmark.markup}</svg>`);
}
await writeFile(join(output, 'framework-mark.svg'),
  `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1400 1400"><title>Framework round module</title><rect width="1400" height="1400" rx="200" fill="#10110f"/><g transform="translate(95 1240) scale(1 -1)">${roundModule('#f3f1eb', '#ffb34a')}</g></svg>`);
const copies = {
  'Manrope-variable.woff2': 'node_modules/@fontsource-variable/manrope/files/manrope-latin-wght-normal.woff2',
  'OFL.txt': 'node_modules/@fontsource/manrope/LICENSE',
  'NOTICE.txt': 'NOTICE.md',
};
for (const [destination, source] of Object.entries(copies)) await copyFile(join(kit, source), join(output, destination));
const sourceFiles = ['scripts/family-identity.mjs', 'scripts/type.mjs', 'scripts/identity.mjs',
  'vendor/firefly/scripts/logo.mjs', 'data/brand.json', 'scripts/layout.mjs',
  'vendor/firefly/scripts/system/machines.mjs', 'vendor/firefly/scripts/system/pieces.mjs',
  'vendor/firefly/scripts/system/architecture.mjs', 'vendor/firefly/scripts/system/pieces.json',
  'vendor/firefly/scripts/system/tokens.json', ...Object.values(copies)];
const hash = async path => createHash('sha256').update(await readFile(path)).digest('hex');
const sources = {};
for (const path of sourceFiles) sources[path] = await hash(join(kit, path));
const exports = {};
for (const name of ['jafly-logo-dark.svg', 'jafly-logo-light.svg', 'jafly-banner-dark.svg', 'jafly-banner-light.svg',
  'jafly-wordmark-dark.svg', 'jafly-wordmark-light.svg', 'framework-mark.svg', ...Object.keys(copies)])
  exports[name] = await hash(join(output, name));
await writeFile(join(output, 'ORIGIN.json'), JSON.stringify({
  repository: 'https://github.com/fireflyframework/brand-kit',
  commit: execFileSync('git', ['rev-parse', 'HEAD'], { cwd: kit, encoding: 'utf8' }).trim(),
  version: JSON.parse(await readFile(join(kit, 'package.json'), 'utf8')).version,
  recipe: 'node scripts/generate_brand.mjs /path/to/brand-kit',
  identity: 'Official outlined jafly familyLogo/familyWordmark; canonical final-y amber light trail; original Firefly endorsement paths',
  sources, exports,
}, null, 2) + '\n');
console.log(`Exported official Jafly artwork and Manrope to ${output}`);

// Labeled guide covers retain the kit's original family paths and outline copy in Manrope.
const { outlined } = await import(pathToFileURL(join(kit, 'scripts/type.mjs')));
const { placed } = await import(pathToFileURL(join(kit, 'vendor/firefly/scripts/logo.mjs')));
const catalog = JSON.parse(await readFile(join(root, 'docs/site/assets/module-catalog.json'), 'utf8'));
const capabilityCovers = JSON.parse(await readFile(join(root, 'docs/site/assets/capabilities.json'), 'utf8'));
const corePages = {
  ...Object.fromEntries(capabilityCovers.map(item => ['capability-' + item.id, item.title])),
  'getting-started': 'Get started', installation: 'Installation and prerequisites',
  'onboarding-event-store': 'Onboarding event store',
  'onboarding-coordination': 'Onboarding coordination',
  architecture: 'Architecture', maven: 'Maven parent and BOM', versioning: 'Versioning',
  migration: 'Move to Boot 4', development: 'Development', examples: 'Examples and tutorials',
  releases: 'Releases', documentation: 'Documentation workflow', brand: 'Brand provenance',
};
function guideBanner(title, kind) {
  const lines = [];
  let line = '';
  for (const word of title.split(' ')) {
    const candidate = line ? `${line} ${word}` : word;
    if (line && outlined(candidate, 0, 0, 56).width > 770) { lines.push(line); line = word; }
    else line = candidate;
  }
  if (line) lines.push(line);
  const logo = familyLogo(brand, 'dark').replace(/<svg[^>]*>/, tag => tag.replace(/ (?:width|height)="[^"]*"/g, ''));
  return `<svg xmlns="http://www.w3.org/2000/svg" width="1400" height="360" viewBox="0 0 1400 360"><title>${title.replaceAll('&', '&amp;')}: jafly</title><rect width="1400" height="360" fill="#10110f"/>${placed(logo, 42, 14, 420)}<path d="M495 65V295" stroke="#62645b"/>${outlined(kind, 550, 93, 21, '#bfb8ab').markup}${lines.map((text, index) => outlined(text, 548, 160 + index * 64, 56, '#f3f1eb', 500).markup).join('')}<path d="M550 330H630" stroke="#ffb34a" stroke-width="3"/></svg>`;
}
for (const [folder, guides] of [
  ['modules', catalog.map(item => [item.slug, item.title])],
  ['banners', Object.entries(corePages)],
]) {
  const directory = join(root, 'docs/site/assets', folder);
  await mkdir(directory, { recursive: true });
  const files = {};
  for (const [slug, title] of guides) {
    const file = `${slug}.svg`;
    await writeFile(join(directory, file), guideBanner(title, folder === 'modules' ? 'Java module guide' : 'Java framework documentation'));
    files[file] = await hash(join(directory, file));
  }
  await writeFile(join(directory, 'ORIGIN.json'), JSON.stringify({
    source: '../brand/ORIGIN.json', recipe: 'scripts/generate_brand.mjs', exports: files,
  }, null, 2) + '\n');
}
console.log(`Exported ${catalog.length} module covers and ${Object.keys(corePages).length} core guide covers`);

const diagramDir = join(root, 'docs/site/assets/diagrams');
await mkdir(diagramDir, { recursive: true });
const label = (text, x, y, size = 22, color = '#272820', weight = 500) => outlined(text, x, y, size, color, weight).markup;
const box = (x, y, width, height, title, detail) => `<rect x="${x}" y="${y}" width="${width}" height="${height}" fill="#f3f1eb" stroke="#bfb8ab"/>${label(title, x + 22, y + 39, 25)}${label(detail, x + 22, y + 73, 17, '#62645b', 400)}`;
const defs = '<defs><marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0 0L10 5L0 10Z" fill="#855414"/></marker></defs>';
const arrow = path => `<path d="${path}" fill="none" stroke="#855414" stroke-width="2" marker-end="url(#arrow)"/>`;
await writeFile(join(diagramDir, 'reactor.svg'), `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1120 650" role="img"><title>Separate source aggregation and consumer management</title>${defs}<rect width="1120" height="650" fill="#f3f1eb"/>${label('In this repository', 40, 43, 21, '#62645b')}${box(40, 74, 300, 113, 'Root reactor', 'pom.xml • not published')}${box(624, 74, 456, 113, 'Modules and nested projects', 'Sources, tests, libraries and samples')}${arrow('M340 131H624')}${label('aggregates', 400, 115, 18, '#855414')}<path d="M40 235H1080" stroke="#bfb8ab"/>${label('In your application', 40, 280, 21, '#62645b')}${box(40, 315, 375, 113, 'Framework parent', 'build/parent • standalone published POM')}${box(40, 485, 375, 113, 'Framework BOM', 'build/bom • standalone published POM')}${box(720, 395, 360, 125, 'Your application', 'Own version and chosen dependencies')}${arrow('M415 372H555V426H720')}${arrow('M415 542H555V485H720')}${label('build + dependency management', 445, 344, 17, '#855414')}${label('dependency versions', 472, 579, 17, '#855414')}${label('Choose either management model. No parent / BOM import cycle.', 40, 634, 18, '#62645b', 400)}</svg>`);
await writeFile(join(diagramDir, 'reactor-narrow.svg'), `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 520 1000" role="img"><title>Source reactor and two independent consumer choices</title>${defs}<rect width="520" height="1000" fill="#f3f1eb"/>${label('In this repository', 24, 40, 22, '#62645b')}${box(24, 65, 472, 110, 'Root reactor', 'pom.xml • not published')}${arrow('M260 175V244')}${label('aggregates', 290, 218, 20, '#855414')}${box(24, 245, 472, 110, 'Modules and nested projects', 'Sources, tests, libraries and samples')}<path d="M24 400H496" stroke="#bfb8ab"/>${label('In your application', 24, 445, 22, '#62645b')}${box(24, 470, 472, 110, 'Framework parent', 'build/parent • standalone published POM')}${label('or', 245, 620, 23, '#62645b')}${box(24, 650, 472, 110, 'Framework BOM', 'build/bom • standalone published POM')}${arrow('M260 760V834')}${label('management', 290, 805, 20, '#855414')}${box(24, 835, 472, 110, 'Your application', 'Own version and chosen dependencies')}${label('Two independent consumer management choices.', 24, 983, 18, '#62645b', 400)}</svg>`);

// Architecture labels stay real SVG text; embedded OFL font keeps the diagrams self-contained.
const fontData = (await readFile(join(output, 'Manrope-variable.woff2'))).toString('base64');
const escape = text => text.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
const text = (value, x, y, size = 22, color = '#f3f1eb', weight = 500) => `<text x="${x}" y="${y}" font-size="${size}" font-weight="${weight}" fill="${color}">${escape(value)}</text>`;
const drawing = (width, height, title, description, content) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${height}" role="img" aria-labelledby="title desc"><title id="title">${title}</title><desc id="desc">${description}</desc><defs><style>@font-face{font-family:Manrope;src:url(data:font/woff2;base64,${fontData}) format('woff2');font-weight:200 800}text{font-family:Manrope,Arial,sans-serif}</style><marker id="flow-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto"><path d="M0 0L10 5L0 10Z" fill="#ffb34a"/></marker></defs><rect width="${width}" height="${height}" fill="#10110f"/>${content}</svg>`;
const rail = (path, optional = false) => `<path d="${path}" fill="none" stroke="${optional ? '#bfb8ab' : '#ffb34a'}" stroke-width="2.5" ${optional ? 'stroke-dasharray="7 6"' : ''} marker-end="url(#flow-arrow)"/>`;
const stationDot = (x,y,n) => `<circle cx="${x}" cy="${y}" r="24" fill="#10110f" stroke="#ffb34a" stroke-width="2"/>${text(String(n), x-7, y+8, 22, '#ffb34a', 650)}`;
const flowDescription = 'A controller sends a command to the bus. The bus validates and runs configured authorization, then calls the application handler and returns its result. Event and cache bridges are conditional. This flow does not guarantee a transaction or exactly-once processing.';
let flow = text('An onboarding application', 40, 52, 30) + text('Solid: command and result. Dashed: optional integration.', 40, 90, 19, '#bfb8ab');
flow += rail('M84 163H1035');
const steps = [
  ['HTTP entry', 'Your controller', 'Spring Boot and WebFlux'],
  ['Command bus', 'Validate and authorize', 'Configured command pipeline'],
  ['Your handler', 'Save the application', 'Return application record'],
  ['Result', 'Return to the caller', 'No implicit transaction'],
];
for (let i=0;i<steps.length;i++) {
  const x=66+i*290; flow+=stationDot(x,163,i+1)+text(steps[i][0],x-25,230,25)+text(steps[i][1],x-25,271,19,'#bfb8ab')+text(steps[i][2],x-25,303,17,'#bfb8ab');
}
flow += text('Optional bridges',42,365,24,'#ffb34a')+text('Handler result',42,407,23)+rail('M70 425V449',true)+text('Event bridge → selected EDA transport',42,484,23)+text('@PublishDomainEvent + event publisher bean',42,518,18,'#bfb8ab')+text('Spring application event',635,407,23)+rail('M663 425V449',true)+text('Query-cache invalidation',635,484,23)+text('Matching event type + cache beans',635,518,18,'#bfb8ab')+text('A command can succeed even when its event publication fails.',42,567,19,'#bfb8ab');
await writeFile(join(diagramDir,'request-flow.svg'), drawing(1200,600,'The Jafly command path',flowDescription,flow));
let mobileFlow=text('An onboarding application',25,46,27)+text('Solid: command. Dashed: optional integration.',25,82,17,'#bfb8ab');
mobileFlow+=rail('M48 135V704');
for(let i=0;i<steps.length;i++) { const y=145+i*174; mobileFlow+=stationDot(48,y,i+1)+text(steps[i][0],93,y+3,28)+text(steps[i][1],93,y+44,20,'#bfb8ab')+text(steps[i][2],93,y+76,18,'#bfb8ab'); }
mobileFlow+=text('Optional bridges',25,825,26,'#ffb34a')+text('Handler result',25,872,22)+rail('M48 891V916',true)+text('Event bridge → EDA transport',25,950,22)+text('Publish annotation + publisher bean',25,984,18,'#bfb8ab')+text('Spring application event',25,1043,22)+rail('M48 1062V1087',true)+text('Query-cache invalidation',25,1121,22)+text('Matching event type + cache beans',25,1155,18,'#bfb8ab')+text('No transaction guarantee across these steps.',25,1220,18,'#bfb8ab');
await writeFile(join(diagramDir,'request-flow-narrow.svg'),drawing(520,1260,'The Jafly command path, vertical layout',flowDescription,mobileFlow));
const responsibilities=[
  ['Spring Boot and WebFlux','#bfb8ab',['HTTP and application lifecycle','Dependency injection','Reactive data foundations']],
  ['Jafly','#ffb34a',['Command and query coordination','Policy ports and provider adapters','Events, workflow and telemetry bridges']],
  ['Your service','#f3f1eb',['Business rules and permission decisions','Transactions, idempotency and recovery','Compensations and provider configuration']],
];
let boundaries='';
for(let i=0;i<3;i++){const y=35+i*190;boundaries+=`<path d="M40 ${y+158}H1160" stroke="#44483b"/>`+text(responsibilities[i][0],40,y+45,30,responsibilities[i][1]);for(let j=0;j<3;j++)boundaries+=text(responsibilities[i][2][j],440,y+35+j*39,22,'#f3f1eb',400);}
await writeFile(join(diagramDir,'service-boundaries.svg'),drawing(1200,610,'Who owns each part of the service','Spring supplies the host and native infrastructure. Jafly coordinates optional application modules. The service owns business decisions and operating policy.',boundaries));
let narrowBoundaries='';
for(let i=0;i<3;i++){const y=30+i*250;narrowBoundaries+=text(responsibilities[i][0],25,y+38,29,responsibilities[i][1])+`<path d="M25 ${y+66}H495" stroke="#44483b"/>`;for(let j=0;j<3;j++)narrowBoundaries+=text(responsibilities[i][2][j],25,y+111+j*42,20,'#f3f1eb',400);}
await writeFile(join(diagramDir,'service-boundaries-narrow.svg'),drawing(520,800,'Service responsibilities, vertical layout','Spring, Jafly and your application retain distinct responsibilities.',narrowBoundaries));
const { machine }=await import(pathToFileURL(join(kit,'vendor/firefly/scripts/system/machines.mjs')));
const { fitDrawing }=await import(pathToFileURL(join(kit,'scripts/layout.mjs')));
const { build,toSVG,tile,brick,ledger }=await import(pathToFileURL(join(kit,'vendor/firefly/scripts/system/pieces.mjs')));
const machineKinds=['transferGate','rulesEngine','dock','backbone','vault','robotArm','scanner','office'];
const capabilityNames=[['Security and','identity'],['Business','operations'],['External','services'],['Event-driven','integration'],['Domain and','data'],['Business','coordination'],['Service','operations'],['Team-wide','architecture']];
let map='',narrowMap='';
for(let i=0;i<8;i++){
 const pieces=i===7?[tile({id:'shared-foundation',size:[7,5],height:0.4,family:'product'}),brick({id:'shared-contracts',at:[0.5,0.6,0.4],size:[2.5,1.5],family:'firefly'}),brick({id:'service-starter',at:[4,0.6,0.4],size:[2.5,1.5],family:'channel'}),ledger({id:'version-catalog',at:[0.5,2.9,0.4],size:[2.5,1.5],family:'core'}),brick({id:'shared-extensions',at:[4,2.9,0.4],size:[2.5,1.5],family:'firefly'})]:machine(machineKinds[i],{id:'capability-'+i}).pieces;
 const scene=toSVG(build(pieces),{scale:45,padding:2,drawLabels:false,theme:'dark'}).replace(/<svg[^>]*>/, tag => tag.replace(/ (?:width|height)="[^"]*"/g, ''));
 const file='capability-'+i+'.svg';await writeFile(join(diagramDir,file),scene);
 const x=35+(i%4)*295,y=25+Math.floor(i/4)*310;
 const wideFit=fitDrawing(scene,{x:x+20,y:y+5,width:220,height:190});
 map+=placed(scene,wideFit.x,wideFit.y,wideFit.width)+text(capabilityNames[i][0],x,y+235,25)+text(capabilityNames[i][1],x,y+269,25);
 const nx=24+(i%2)*260,ny=20+Math.floor(i/2)*290;
 const narrowFit=fitDrawing(scene,{x:nx+5,y:ny+5,width:210,height:190});
 narrowMap+=placed(scene,narrowFit.x,narrowFit.y,narrowFit.width)+text(capabilityNames[i][0],nx,ny+221,22)+text(capabilityNames[i][1],nx,ny+251,22);
}
await writeFile(join(diagramDir,'capability-map.svg'),drawing(1200,660,'The eight Jafly capability areas','Illustrations group the eight capabilities. Positions carry no runtime dependency or deployment meaning.',map+text('Choose modules and adapters for the operations in your service.',35,641,17,'#bfb8ab')));
await writeFile(join(diagramDir,'capability-map-narrow.svg'),drawing(550,1240,'The eight capability areas, compact layout','Conceptual illustrations of security, business operations, services, events, domain data, coordination, operations and shared architecture.',narrowMap+text('Positions carry no runtime dependency meaning.',24,1220,17,'#bfb8ab')));
const eventStoreDescription = 'The onboarding aggregate produces domain events. R2dbcEventStore appends them with an expected version in the configured PostgreSQL transaction. Loading ordered typed events reconstructs a fresh aggregate.';
const historySteps = [
  ['Your aggregate', 'submit or approve', 'Application-owned state rules'],
  ['R2dbcEventStore', 'Append with expected version', 'One configured transaction'],
  ['PostgreSQL events', 'Commit the event rows', 'Unique aggregate/version'],
  ['Load the stream', 'Read in aggregate-version order', 'No snapshot used in this example'],
  ['Decode domain events', 'Explicit Jackson type registration', 'Preserve the concrete event types'],
  ['Fresh aggregate', 'Replay with loadFromHistory', 'Reconstruct state and version'],
];
let history=text('Persist the onboarding history',40,52,30)+text('Write the facts; rebuild application state from the stored sequence.',40,90,19,'#bfb8ab');
for(let side=0;side<2;side++){
 const x=65+side*590;
 history+=text(side===0?'Write':'Read',x-25,137,24,'#ffb34a')+rail(`M${x} 190V470`);
 for(let row=0;row<3;row++){
  const i=side*3+row,y=195+row*140;
  history+=stationDot(x,y,i+1)+text(historySteps[i][0],x+45,y+4,25)+text(historySteps[i][1],x+45,y+42,19,'#bfb8ab')+text(historySteps[i][2],x+45,y+73,17,'#bfb8ab');
 }
}
history+=text('The tests prove one append transaction. External calls and broker delivery need their own policy.',40,590,18,'#bfb8ab');
await writeFile(join(diagramDir,'event-store.svg'),drawing(1200,625,'Persist and reconstruct an onboarding application',eventStoreDescription,history));
let narrowHistory=text('Persist the onboarding history',25,46,27)+text('Store facts. Replay application state.',25,82,18,'#bfb8ab');
for(let side=0;side<2;side++){
 const start=side===0?185:780;
 narrowHistory+=text(side===0?'Write':'Read',25,start-50,26,'#ffb34a')+rail(`M48 ${start}V${start+280}`);
 for(let row=0;row<3;row++){
  const i=side*3+row,y=start+row*140;
  narrowHistory+=stationDot(48,y,i+1)+text(historySteps[i][0],93,y+4,27)+text(historySteps[i][1],93,y+43,19,'#bfb8ab')+text(historySteps[i][2],93,y+76,17,'#bfb8ab');
 }
}
narrowHistory+=text('One configured append transaction is tested.',25,1200,18,'#bfb8ab');
await writeFile(join(diagramDir,'event-store-narrow.svg'),drawing(520,1240,'Event persistence and replay, vertical layout',eventStoreDescription,narrowHistory));
const coordinationDescription = 'Three independent onboarding examples. A Saga compensates a recorded application when eligibility fails. TCC confirms successful reservations or cancels held reservations after a try failure. A Workflow orders validation, document checking and the review result.';
let coordination=text('Choose a coordination model',40,52,32)+text('Each example runs independently with application-owned providers.',40,94,23,'#bfb8ab');
coordination+='<path d="M410 140V610M820 140V610" stroke="#44483b"/>';
coordination+=text('Saga',40,153,32,'#ffb34a')+rail('M65 220V330')+stationDot(65,220,1)+stationDot(65,330,2)+text('Record application',110,228,23)+text('Check eligibility',110,338,23)+text('If eligibility fails',110,405,23,'#bfb8ab')+rail('M65 356V448',true)+text('Remove application record',110,477,23);
coordination+=text('TCC',435,153,32,'#ffb34a')+rail('M460 220V330')+stationDot(460,220,1)+stationDot(460,330,2)+text('Try reviewer reservation',505,228,23)+text('Try document reservation',505,338,23)+text('Both succeed',505,405,23,'#bfb8ab')+rail('M460 356V448')+text('Confirm reservations',505,477,23)+rail('M460 350H800V560H460',true)+text('If a try fails',505,535,23,'#bfb8ab')+text('Cancel held reservations',505,594,23);
coordination+=text('Workflow',845,153,32,'#ffb34a')+rail('M870 220V440')+stationDot(870,220,1)+stationDot(870,330,2)+stationDot(870,440,3)+text('Validate applicant',915,228,23)+text('Check documents',915,338,23)+text('Record ready for review',915,448,23);
coordination+=text('Compensations and reservation actions are business operations supplied by the application.',40,658,23,'#bfb8ab');
await writeFile(join(diagramDir,'onboarding-coordination.svg'),drawing(1240,695,'Independent Saga, TCC and Workflow examples',coordinationDescription,coordination));
let narrowCoordination=text('Three coordination models',25,46,29)+text('Each runs independently.',25,85,22,'#bfb8ab');
narrowCoordination+=text('Compensate with a Saga',25,147,31,'#ffb34a')+rail('M48 210V315')+stationDot(48,210,1)+stationDot(48,315,2)+text('Record application',93,218,24)+text('Check eligibility',93,323,24)+text('On eligibility failure',93,383,22,'#bfb8ab')+rail('M48 341V415',true)+text('Remove application record',93,450,24)+text('Application-owned compensation',93,489,22,'#bfb8ab')+'<path d="M25 540H495" stroke="#44483b"/>';
narrowCoordination+=text('Reserve with TCC',25,598,31,'#ffb34a')+rail('M48 665V770')+stationDot(48,665,1)+stationDot(48,770,2)+text('Try reviewer reservation',93,673,24)+text('Try document reservation',93,778,24)+text('Both tries succeed',25,840,22,'#bfb8ab')+rail('M48 860V885')+text('Confirm both reservations',93,920,24)+text('A try fails',25,990,22,'#bfb8ab')+rail('M48 1010V1035',true)+text('Cancel held reservations',93,1070,24)+text('Application-owned cancel actions',93,1109,22,'#bfb8ab')+'<path d="M25 1160H495" stroke="#44483b"/>';
narrowCoordination+=text('Order a Workflow',25,1220,31,'#ffb34a')+rail('M48 1280V1500')+stationDot(48,1280,1)+stationDot(48,1390,2)+stationDot(48,1500,3)+text('Validate applicant',93,1288,24)+text('Check documents',93,1398,24)+text('Record ready for review',93,1508,24)+text('Choose recovery for a failed execution.',25,1605,22,'#bfb8ab');
await writeFile(join(diagramDir,'onboarding-coordination-narrow.svg'),drawing(520,1650,'Independent coordination models, vertical layout',coordinationDescription,narrowCoordination));
const diagramExports={};
for(const name of ['onboarding-coordination.svg','onboarding-coordination-narrow.svg','event-store.svg','event-store-narrow.svg','reactor.svg','reactor-narrow.svg','request-flow.svg','request-flow-narrow.svg','service-boundaries.svg','service-boundaries-narrow.svg','capability-map.svg','capability-map-narrow.svg',...machineKinds.map((_,i)=>'capability-'+i+'.svg')])diagramExports[name]=await hash(join(diagramDir,name));
await writeFile(join(diagramDir,'ORIGIN.json'),JSON.stringify({source:'../brand/ORIGIN.json',recipe:'scripts/generate_brand.mjs',exports:diagramExports},null,2)+'\n');
