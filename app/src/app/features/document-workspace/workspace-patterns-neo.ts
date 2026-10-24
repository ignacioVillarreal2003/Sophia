import { WorkspacePattern } from './workspace-patterns';

/**
 * Forty-five marks after the "Geometrica Neo Geo kit" sheet, numbered as in it (151 ... 300). Each is drawn on the same
 * 48 x 48 tile as the others, with `currentColor` for the ink and `var(--wl-bg)` for what shows through.
 */
const BG = 'var(--wl-bg, #ece8dd)';
const FILL = 'fill="currentColor" stroke="none"';
const LINE = (w: number) => `fill="none" stroke="currentColor" stroke-width="${w}" stroke-linecap="round" stroke-linejoin="round"`;
const range = (n: number) => Array.from({ length: n }, (_, i) => i);
const join = (items: string[]) => items.join('');
const f = (n: number) => +n.toFixed(2);

/** The same markup turned `n` times around a point. */
function spin(n: number, markup: string, cx = 24, cy = 24, from = 0): string {
  return join(range(n).map((i) => `<g transform="rotate(${f(from + (360 / n) * i)} ${cx} ${cy})">${markup}</g>`));
}

function poly(points: string): string {
  return `<path ${FILL} d="${points}"/>`;
}

/** The outline of a rounded, concave-sided square used by 166, scaled about the middle. */
function dish(scale: number): string {
  return `<path transform="translate(24 24) scale(${scale}) translate(-24 -24)" d="M7 7Q24 9.5 41 7Q38.5 24 41 41Q24 38.5 7 41Q9.5 24 7 7Z"/>`;
}

export const NEO_PATTERNS: readonly WorkspacePattern[] = [
  // ---- 151 - 155
  { id: 'n151', svg: `<g ${LINE(4.6)}><path stroke-linecap="butt" d="M29 6V13Q29 19 23 19H6"/><path stroke-linecap="butt" d="M19 42V35Q19 29 25 29H42"/></g>` },
  {
    id: 'n152',
    svg:
      `<g ${LINE(3.2)}><path d="M24 10H38M10 10L24 24M10 24V38M38 24V38"/></g>` +
      join([10, 24, 38].flatMap((y) => [10, 24, 38].map((x) => `<circle ${FILL} cx="${x}" cy="${y}" r="4.4"/>`))),
  },
  { id: 'n153', svg: `<g ${LINE(1.1)}>` + join([0, 2.1, 4.2, 6.3, 8.4].map((o) => `<rect x="${6 + o}" y="${14 + o}" width="${36 - 2 * o}" height="${20 - 2 * o}" rx="${10 - o}"/>`)) + `</g>` },
  {
    id: 'n154',
    svg: `<g ${LINE(1)}>${join([0, 1.9, 3.8, 5.7, 7.6].map((o) => `<path d="M${6 + o} 42V24C${6 + o} 4 ${24 - o} 4 ${24 - o} 24V36C${24 - o} 46 ${42 - o} 46 ${42 - o} 36V8"/>`))}</g>`,
  },
  {
    id: 'n155',
    svg: `<g ${LINE(0.8)}><rect x="6" y="6" width="36" height="36"/>${join(range(7).map((i) => `<path d="M${6 + (i + 1) * 4.5} 6V42M6 ${6 + (i + 1) * 4.5}H42"/>`))}</g>`,
  },

  // ---- 166 - 170
  { id: 'n166', svg: `<g ${LINE(2.3)}>${[1, 0.8, 0.62, 0.45, 0.3, 0.16].map(dish).join('')}</g>` },
  {
    id: 'n167',
    svg:
      `<g ${LINE(0.8)}>` +
      join([0, 1, 2, 3, 4, 5, 6].flatMap((i) => [`<path d="M6 ${6 + i * 6}L24 24"/>`, `<path d="M42 ${6 + i * 6}L24 24"/>`])) +
      join([11, 15.5, 19.5, 22.5].flatMap((x) => [`<path d="M${x} ${x}V${48 - x}"/>`, `<path d="M${48 - x} ${x}V${48 - x}"/>`])) +
      `</g>`,
  },
  {
    id: 'n168',
    svg:
      `<g ${LINE(1.1)}><path d="M6 6V42H42M6 24H24V42M24 24V42M42 6L10 42M42 6L24 42M42 6L6 32M42 6L24 24"/></g>` +
      `<path ${FILL} d="M6 42V36A6 6 0 0 0 12 42Z"/>`,
  },
  {
    id: 'n169',
    svg: join(
      range(10).map((i) => {
        const top = i < 4 ? 6 : 6 + (i - 3) * 5.4;
        const bottom = i < 5 ? 14 + i * 6.5 : 42;
        return `<rect ${FILL} x="${6 + i * 3.7}" y="${top}" width="2.8" height="${f(Math.min(bottom, 42) - top)}" rx="1.4"/>`;
      }),
    ),
  },
  { id: 'n170', svg: poly('M6 15L15 6H23V23H6Z') + poly('M25 6H42V23H34L25 14Z') + poly('M6 25H14L23 34V42H6Z') + poly('M25 25H42V42H33L25 34Z') },
  // ---- 181 - 185
  { id: 'n181', svg: poly('M6 12Q6 8 12 8H42V22H30Q24 22 22 18Q20 14 14 14H6Z') + poly('M42 36Q42 40 36 40H6V26H18Q24 26 26 30Q28 34 34 34H42Z') + `<g ${LINE(1.4)}><path d="M6 5H42"/><path d="M6 43H42"/></g>` },
  {
    id: 'n182',
    svg: `<g ${LINE(3.2)}><rect x="8" y="8" width="32" height="32" rx="5"/><path d="M12 38H38V12H12V34H34V16H16V30H30V20H20V26H26V23"/></g>`,
  },
  { id: 'n183', svg: `<g ${LINE(1.2)}><path d="M18 6H14Q6 6 6 14V34Q6 42 14 42H34Q42 42 42 34V14Q42 6 34 6H30"/></g>` },
  { id: 'n184', svg: poly('M6 17Q6 14 10 14H24V6H42V26H24V34Q24 38 28 38H42V42H6Z') },
  { id: 'n185', svg: join([6, 19.5, 33].map((x, i) => poly(`M${x} 6H${x + 9}V${i === 1 ? 25 : 18}L${x} ${i === 1 ? 18 : 25}Z`) + poly(`M${x} ${i === 1 ? 20 : 27}L${x + 9} ${i === 1 ? 27 : 20}V42H${x}Z`))) },
  // ---- 176 - 180
  {
    id: 'n176',
    svg:
      `<circle cx="24" cy="24" r="18" ${LINE(1)}/>` +
      join([14.6, 11.2, 7.8].map((r) => `<circle cx="24" cy="24" r="${r}" ${LINE(2.2)}/>`)) +
      `<circle cx="24" cy="24" r="1.6" ${FILL}/>`,
  },
  {
    id: 'n177',
    svg: poly('M6 6H22V24Q22 26 24 26H28V30H12Q6 30 6 24Z') + poly('M6 30H12Q12 38 20 38H42V42H6Z') + poly('M40 6H42V42H40Z'),
  },
  {
    id: 'n178',
    svg: join(
      range(9).map((i) => {
        const y = 6 + i * 4.1;
        return i % 2 === 0
          ? `<rect ${FILL} x="6" y="${f(y)}" width="12" height="2.6"/><rect ${FILL} x="30" y="${f(y)}" width="12" height="2.6"/>`
          : `<rect ${FILL} x="6" y="${f(y)}" width="36" height="2.6"/>`;
      }),
    ) + `<rect ${FILL} x="19.5" y="6" width="9" height="36" opacity="0"/>`,
  },
  {
    id: 'n179',
    svg:
      `<g ${LINE(1)}>` +
      [[6, 6], [25, 6], [6, 25], [25, 25]].map(([x, y]) => `<rect x="${x}" y="${y}" width="17" height="17" rx="4"/><path d="M${x + 1} ${y + 1}L${x + 16} ${y + 16}"/>`).join('') +
      `</g>`,
  },
  {
    id: 'n180',
    svg: join(
      [0, 0.13, 0.26, 0.39, 0.5, 0.61, 0.74, 0.87, 1].flatMap((t) => [
        `<circle ${FILL} cx="${f(8 + 32 * t)}" cy="${f(8 + 32 * t)}" r="1.5"/>`,
        t === 0.5 ? '' : `<circle ${FILL} cx="${f(40 - 32 * t)}" cy="${f(8 + 32 * t)}" r="1.5"/>`,
      ]),
    ),
  },

  // ---- 191 - 195
  { id: 'n191', svg: `<g ${LINE(9)}><path stroke-linecap="butt" d="M6 11H13Q23 11 23 19H42"/><path stroke-linecap="butt" d="M6 37H13Q23 37 23 29H42"/></g>` },
  {
    id: 'n192',
    svg: `<g ${LINE(1)}>${join(range(11).map((i) => `<path d="M6 ${f(27 + i * 1.5)}C14 ${f(27 + i * 1.5)} 16 ${f(8 + i * 3)} 24 ${f(8 + i * 3)}S34 ${f(27 + i * 1.5)} 42 ${f(27 + i * 1.5)}"/>`))}</g>`,
  },
  {
    id: 'n193',
    svg: join(range(11).map((i) => `<rect ${FILL} x="${f(6 + i * 3.4)}" y="${f(6 + i * 2.1)}" width="2.2" height="16" rx="1.1"/>`)),
  },
  { id: 'n194', svg: join(range(5).map((i) => poly(`M6 ${6 + i * 7.5}H42V${6 + i * 7.5 + 4.6}H6Z`))) + `<path d="M6 10C16 10 18 18 24 24S34 36 42 36" fill="none" stroke="${BG}" stroke-width="2.6"/>` },
  {
    id: 'n195',
    svg: `<g ${LINE(1.1)}>${join(range(26).map((i) => { const a = (i / 26) * Math.PI * 2; const r2 = i % 2 ? 15 : 18; return `<path d="M${f(24 + Math.cos(a) * 2)} ${f(24 + Math.sin(a) * 2)}L${f(24 + Math.cos(a) * r2)} ${f(24 + Math.sin(a) * r2)}"/>`; }))}</g>`,
  },

  // ---- 206 - 210
  {
    id: 'n206',
    svg:
      poly('M6 14Q6 6 14 6H42V34Q42 42 34 42H6Z') +
      `<rect x="10" y="10" width="28" height="28" fill="${BG}"/><rect x="13" y="13" width="22" height="22" ${FILL}/>` +
      `<circle cx="24" cy="24" r="10" fill="${BG}"/><path d="M17 24Q24 14 31 24Q24 34 17 24Z" ${FILL}/>`,
  },
  {
    id: 'n207',
    svg:
      `<g ${LINE(2.4)}><path stroke-linecap="butt" d="M42 6V42H6M36 6V36H6M30 12V30H12"/><rect x="10" y="10" width="16" height="16" stroke-linecap="butt"/><rect x="15" y="15" width="6" height="6" stroke-linecap="butt"/></g>`,
  },
  {
    id: 'n208',
    svg:
      `<g ${LINE(1)}>` +
      [[15, 15], [33, 15], [15, 33], [33, 33]].map(([x, y]) => `<circle cx="${x}" cy="${y}" r="9"/><ellipse cx="${x}" cy="${y}" rx="9" ry="6"/><ellipse cx="${x}" cy="${y}" rx="9" ry="3"/>`).join('') +
      `</g>`,
  },
  {
    id: 'n209',
    svg: join(range(6).map((i) => { const y = 6 + i * 6; return i % 2 === 0 ? poly(`M6 ${y}L42 ${y + 2}L6 ${y + 5}Z`) : poly(`M42 ${y}L6 ${y + 2}L42 ${y + 5}Z`); })),
  },
  { id: 'n210', svg: join(range(5).map((i) => poly(`M${6 + i * 3.6} 42L${7.8 + i * 3.6} 6L${9.6 + i * 3.6} 42Z`))) + poly('M26 6H42V9H26Z') + join(range(4).map((i) => poly(`M${26 + i * 4} 8L${28 + i * 4} 42L${30 + i * 4} 8Z`))) },
  // ---- 266 - 270
  { id: 'n266', svg: spin(7, `<path ${FILL} d="M24 24L20.4 8Q24 4.6 27.6 8Z"/>`) },
  { id: 'n267', svg: poly('M6 6H42V42H6Z') + `<rect x="14.5" y="18" width="3" height="24" fill="${BG}"/><rect x="30" y="18" width="3" height="24" fill="${BG}"/>` },
  {
    id: 'n268',
    svg:
      `<g ${LINE(3)}><path stroke-linecap="butt" d="M24 10V38M10 24H38"/></g>` +
      join([[24, 9], [39, 24], [24, 39], [9, 24]].map(([x, y]) => `<circle ${FILL} cx="${x}" cy="${y}" r="5"/>`)) +
      `<circle ${FILL} cx="24" cy="24" r="4"/>`,
  },
  { id: 'n269', svg: poly('M6 6H17Q24 15 31 6H42V18Q33 24 42 30V42H31Q24 33 17 42H6V30Q15 24 6 18Z') },
  { id: 'n270', svg: `<g ${LINE(2.2)}><path stroke-linecap="butt" d="M6 7H42V41H7V13H36V35H13V19H30V29H19"/></g>` },

  // ---- 281 - 285
  { id: 'n281', svg: poly('M6 6H30V18H42V42H18V30H6Z') + `<rect x="12" y="18" width="6" height="6" fill="${BG}"/><rect x="24" y="30" width="6" height="6" fill="${BG}"/>` },
  {
    id: 'n282',
    svg: join(
      range(7).map((i) => {
        const y = 10 + i * 5;
        const h = 6 - i * 0.55;
        return `<path ${FILL} d="M6 ${y}H18Q22 ${y} 24 ${f(y - h)}Q26 ${y} 30 ${y}H42V${y + 1}H6Z"/>`;
      }),
    ),
  },
  { id: 'n283', svg: poly('M6 8H13Q18 12 18 24T13 40H6Z') + poly('M42 8H35Q30 12 30 24T35 40H42Z') + poly('M21 8H23V40H21Z') + poly('M25 6H27V42H25Z') },
  { id: 'n284', svg: poly('M6 14Q24 -3 42 14Q24 31 6 14Z') + poly('M6 34Q24 17 42 34Q24 51 6 34Z') },
  {
    id: 'n285',
    svg:
      poly('M24 6Q26 22 42 24Q26 26 24 42Q22 26 6 24Q22 22 24 6Z') +
      poly('M16 12Q17 20 22 24Q17 22 16 12Z') + poly('M32 12Q31 20 26 24Q31 22 32 12Z') + poly('M16 36Q17 28 22 24Q17 26 16 36Z') + poly('M32 36Q31 28 26 24Q31 26 32 36Z'),
  },

  // ---- 296 - 300
  {
    id: 'n296',
    svg: `<circle ${FILL} cx="24" cy="24" r="18"/><circle cx="24" cy="24" r="12.5" fill="none" stroke="${BG}" stroke-width="1.6"/><circle cx="24" cy="24" r="7" fill="none" stroke="${BG}" stroke-width="1.6"/><circle cx="24" cy="24" r="2.4" fill="none" stroke="${BG}" stroke-width="1.4"/>`,
  },
  { id: 'n297', svg: spin(5, `<path ${FILL} d="M22.2 24L20 6H28L25.8 24Z"/>`) },
  { id: 'n298', svg: poly('M16 6H32L42 16V32L32 42H16L6 32V16Z') + `<path d="M15 33L33 15" stroke="${BG}" stroke-width="1.8" stroke-linecap="round"/>` },
  { id: 'n299', svg: spin(10, `<path ${FILL} d="M24 24L20.5 6.5H28Z"/>`) },
  { id: 'n300', svg: spin(8, `<path ${FILL} d="M24 24C19 18 18 10 24 5C30 10 29 18 24 24Z"/>`) },
];
