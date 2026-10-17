import { describe, expect, it } from 'vitest';
import { BoardElement, arrowEnds, arrowGeometry, borderPoint, elementBounds, hitElement, parseBoard, resolveStyle, scaleInto, simplifyStroke, translate, wrapLines } from './board.model';
import { buildSvg, esc, renderElement } from './board-render';
import { buildTemplate, TEMPLATE_IDS } from './board-templates';

function el(partial: Partial<BoardElement>): BoardElement {
  return { id: 'e1', type: 'box', x: 0, y: 0, w: 100, h: 60, text: '', color: 'blue', ...partial };
}

const none = () => undefined;

describe('board model', () => {
  it('reads old boards: missing style fields fall back to the color preset', () => {
    const board = parseBoard(JSON.stringify({ version: 1, elements: [{ id: 'a', type: 'box', x: 0, y: 0, w: 10, h: 10, text: 'x', color: 'green' }] }));
    expect(board.version).toBe(2);
    expect(resolveStyle(board.elements[0]).fill).toBe('#bbf7d0');
  });

  it('ignores garbage instead of throwing', () => {
    expect(parseBoard('nope').elements).toEqual([]);
    expect(parseBoard('{"elements":[{"x":1},{"id":"a","type":"note"}]}').elements).toHaveLength(1);
  });

  it('wraps text and keeps explicit line breaks', () => {
    expect(wrapLines('uno dos tres', 7)).toEqual(['uno dos', 'tres']);
    expect(wrapLines('a\nb', 10)).toEqual(['a', 'b']);
    expect(wrapLines('abcdefghij', 4)).toEqual(['abcd', 'efgh', 'ij']);
  });

  it('hit testing: hollow shapes only react on their outline, filled ones inside', () => {
    const hollow = el({ fill: 'none' });
    const filled = el({ fill: '#ffffff' });
    expect(hitElement(hollow, { x: 50, y: 30 }, 4, none)).toBe(false);
    expect(hitElement(hollow, { x: 1, y: 30 }, 4, none)).toBe(true);
    expect(hitElement(filled, { x: 50, y: 30 }, 4, none)).toBe(true);
    expect(hitElement(filled, { x: 200, y: 30 }, 4, none)).toBe(false);
  });

  it('an ellipse does not react in its corners', () => {
    const ellipse = el({ type: 'ellipse', fill: '#fff' });
    expect(hitElement(ellipse, { x: 50, y: 30 }, 2, none)).toBe(true);
    expect(hitElement(ellipse, { x: 3, y: 3 }, 2, none)).toBe(false);
  });

  it('arrows follow the shapes they are glued to', () => {
    const a = el({ id: 'a', x: 0, y: 0, w: 100, h: 100 });
    const b = el({ id: 'b', x: 300, y: 0, w: 100, h: 100 });
    const arrow = el({ id: 'x', type: 'arrow', from: 'a', to: 'b', w: 0, h: 0 });
    const lookup = (id?: string) => [a, b].find((e) => e.id === id);
    const ends = arrowEnds(arrow, lookup);
    expect(ends.a.x).toBeCloseTo(104, 0);
    expect(ends.b.x).toBeCloseTo(296, 0);
    expect(ends.a.y).toBeCloseTo(50, 0);
    // moving b moves the end of the arrow without touching the arrow itself
    const moved = translate(b, 0, 200);
    const after = arrowEnds(arrow, (id) => [a, moved].find((e) => e.id === id));
    expect(after.b.y).toBeGreaterThan(50);
  });

  it('elbow and curved arrows end where the straight one does', () => {
    const arrow = el({ type: 'arrow', x: 0, y: 0, x2: 100, y2: 80, route: 'elbow', w: 0, h: 0 });
    const elbow = arrowGeometry(arrow, none);
    expect(elbow.pts).toHaveLength(4);
    expect(elbow.b).toEqual({ x: 100, y: 80 });
    expect(arrowGeometry({ ...arrow, route: 'curve' }, none).pts).toHaveLength(3);
  });

  it('borderPoint stops at the outline of a diamond', () => {
    const d = el({ type: 'diamond', x: 0, y: 0, w: 100, h: 100 });
    const p = borderPoint(d, { x: 300, y: 50 }, 0);
    expect(p.x).toBeCloseTo(100, 0);
  });

  it('scaling a selection maps every element into the new box', () => {
    const scaled = scaleInto(el({ x: 10, y: 10, w: 20, h: 20 }), { x: 0, y: 0, w: 100, h: 100 }, { x: 0, y: 0, w: 200, h: 50 });
    expect(scaled).toMatchObject({ x: 20, y: 5, w: 40, h: 10 });
  });

  it('simplifies strokes but keeps both ends', () => {
    const stroke: [number, number][] = [[0, 0], [0.5, 0], [1, 0], [10, 0], [10.5, 0], [20, 0]];
    const out = simplifyStroke(stroke, 2);
    expect(out[0]).toEqual([0, 0]);
    expect(out[out.length - 1]).toEqual([20, 0]);
    expect(out.length).toBeLessThan(stroke.length);
  });

  it('bounds of a stroke come from its points', () => {
    const pen = el({ type: 'pen', points: [[5, 5], [25, 45]] });
    expect(elementBounds(pen, none)).toEqual({ x: 5, y: 5, w: 20, h: 40 });
  });
});

describe('board render', () => {
  it('escapes user text so a shared board cannot inject markup', () => {
    const markup = renderElement(el({ text: '<i>x</i>', w: 400 }), none);
    expect(markup).not.toContain('<i>');
    expect(markup).toContain('&lt;i&gt;');
    expect(esc('"&\'')).toBe('&quot;&amp;&#39;');
  });

  it('refuses unsafe colors and image sources', () => {
    const colored = renderElement(el({ stroke: 'red" onload="x', fill: 'url(javascript:1)' }), none);
    expect(colored).not.toContain('onload');
    const image = renderElement(el({ type: 'image', src: 'javascript:alert(1)' }), none);
    expect(image).not.toContain('javascript');
  });

  it('draws hand-drawn shapes the same way every time', () => {
    const shape = el({ rough: 2 });
    expect(renderElement(shape, none)).toBe(renderElement(shape, none));
    expect(renderElement(shape, none)).not.toBe(renderElement({ ...shape, rough: 0 }, none));
  });

  it('builds a standalone SVG with the chosen elements only', () => {
    const board = parseBoard(JSON.stringify({ elements: [el({ id: 'a', text: 'uno' }), el({ id: 'b', x: 500, text: 'dos' })] }));
    const all = buildSvg(board)!;
    const one = buildSvg(board, { ids: ['a'] })!;
    expect(all.markup.startsWith('<svg')).toBe(true);
    expect(all.width).toBeGreaterThan(one.width);
    expect(one.markup).toContain('uno');
    expect(one.markup).not.toContain('dos');
    expect(buildSvg(parseBoard('{}'))).toBeNull();
  });

  it('every template produces elements whose glued arrows point at existing ones', () => {
    for (const id of TEMPLATE_IDS) {
      const els = buildTemplate(id, (k) => k, 'Tema');
      expect(els.length).toBeGreaterThan(0);
      const ids = new Set(els.map((e) => e.id));
      for (const e of els) {
        if (e.from) expect(ids.has(e.from)).toBe(true);
        if (e.to) expect(ids.has(e.to)).toBe(true);
      }
    }
  });
});
