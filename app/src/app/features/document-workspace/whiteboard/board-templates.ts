import { Board, BoardColor, BoardElement, DEFAULT_SIZE, ElementType, newId, serializeBoard } from './board.model';

export type TemplateId = 'mindmap' | 'flowchart' | 'swot' | 'kanban' | 'cornell' | 'timeline' | 'venn' | 'concepts';

export const TEMPLATE_IDS: TemplateId[] = ['mindmap', 'concepts', 'flowchart', 'swot', 'kanban', 'cornell', 'timeline', 'venn'];

type Translate = (key: string) => string;

function make(type: ElementType, x: number, y: number, w: number, h: number, text = '', extra: Partial<BoardElement> = {}): BoardElement {
  return { id: newId(), type, x, y, w, h, text, color: 'yellow', ...extra };
}

function link(a: BoardElement, b: BoardElement, text = '', extra: Partial<BoardElement> = {}): BoardElement {
  return make('arrow', 0, 0, 0, 0, text, { from: a.id, to: b.id, route: 'straight', ...extra });
}

function note(x: number, y: number, text: string, color: BoardColor): BoardElement {
  return make('note', x, y, DEFAULT_SIZE.note.w, DEFAULT_SIZE.note.h, text, { color });
}

/** Elements of a template laid out around (0, 0); the caller shifts them to where they should land. */
export function buildTemplate(id: TemplateId, t: Translate, centerText = ''): BoardElement[] {
  switch (id) {
    case 'mindmap': {
      const mid = make('ellipse', -110, -60, 220, 120, centerText || t('board.tpl.centralIdea'), { color: 'violet', fill: '#ddd6fe', stroke: '#7c3aed', strokeWidth: 3 });
      const spots: [number, number, BoardColor][] = [
        [-380, -230, 'yellow'],
        [200, -230, 'blue'],
        [-380, 100, 'green'],
        [200, 100, 'rose'],
      ];
      const branches = spots.map(([x, y, c], i) => note(x, y, `${t('board.tpl.branch')} ${i + 1}`, c));
      return [...branches.map((b) => link(mid, b, '', { route: 'curve' })), mid, ...branches];
    }
    case 'concepts': {
      const a = make('box', -420, -40, 200, 80, t('board.tpl.conceptA'), { fill: '#bfdbfe', stroke: '#2563eb', round: true });
      const b = make('box', -100, -40, 200, 80, t('board.tpl.conceptB'), { fill: '#bbf7d0', stroke: '#16a34a', round: true });
      const c = make('box', 220, -40, 200, 80, t('board.tpl.conceptC'), { fill: '#fde68a', stroke: '#d97706', round: true });
      return [link(a, b, t('board.tpl.relation')), link(b, c, t('board.tpl.relation')), a, b, c];
    }
    case 'flowchart': {
      const start = make('ellipse', -90, -300, 180, 70, t('board.tpl.start'), { fill: '#bbf7d0', stroke: '#16a34a' });
      const step = make('box', -100, -170, 200, 80, t('board.tpl.process'), { fill: '#bfdbfe', stroke: '#2563eb', round: true });
      const decision = make('diamond', -110, -30, 220, 130, t('board.tpl.decision'), { fill: '#fde68a', stroke: '#d97706' });
      const yes = make('box', -420, 170, 200, 80, t('board.tpl.optionYes'), { fill: '#bfdbfe', stroke: '#2563eb', round: true });
      const no = make('box', 220, 170, 200, 80, t('board.tpl.optionNo'), { fill: '#fecaca', stroke: '#dc2626', round: true });
      const end = make('ellipse', -90, 330, 180, 70, t('board.tpl.end'), { fill: '#e2e8f0', stroke: '#475569' });
      return [
        link(start, step),
        link(step, decision),
        link(decision, yes, t('board.tpl.yes'), { route: 'elbow' }),
        link(decision, no, t('board.tpl.no'), { route: 'elbow' }),
        link(yes, end, '', { route: 'elbow' }),
        link(no, end, '', { route: 'elbow' }),
        start,
        step,
        decision,
        yes,
        no,
        end,
      ];
    }
    case 'swot': {
      const cells: [string, number, number, string, BoardColor][] = [
        [t('board.tpl.strengths'), -400, -300, '#bbf7d0', 'green'],
        [t('board.tpl.weaknesses'), 20, -300, '#fecaca', 'rose'],
        [t('board.tpl.opportunities'), -400, 40, '#bfdbfe', 'blue'],
        [t('board.tpl.threats'), 20, 40, '#fde68a', 'yellow'],
      ];
      return cells.flatMap(([title, x, y, fill, color]) => [
        make('frame', x, y, 380, 300, title, { fill, stroke: '#64748b' }),
        note(x + 20, y + 20, '', color),
      ]);
    }
    case 'kanban': {
      const columns = [t('board.tpl.todo'), t('board.tpl.doing'), t('board.tpl.done')];
      const colors: BoardColor[] = ['rose', 'yellow', 'green'];
      return columns.flatMap((title, i) => [
        make('frame', -480 + i * 330, -260, 300, 520, title, { fill: '#f1f5f9', stroke: '#64748b' }),
        note(-460 + i * 330, -230, '', colors[i]),
      ]);
    }
    case 'cornell': {
      return [
        make('frame', -440, -300, 240, 460, t('board.tpl.cues'), { fill: '#e2e8f0', stroke: '#64748b' }),
        make('frame', -180, -300, 620, 460, t('board.tpl.notes'), { fill: '#ffffff', stroke: '#64748b' }),
        make('frame', -440, 190, 880, 140, t('board.tpl.summary'), { fill: '#fef9c3', stroke: '#ca8a04' }),
      ];
    }
    case 'timeline': {
      const line = make('line', -460, 0, 0, 0, '', { x2: 460, y2: 0, strokeWidth: 4, endHead: 'triangle' });
      const colors: BoardColor[] = ['rose', 'yellow', 'green', 'blue', 'violet'];
      const marks = colors.flatMap((c, i) => {
        const x = -400 + i * 200;
        const up = i % 2 === 0;
        return [
          make('ellipse', x - 8, -8, 16, 16, '', { fill: '#1e293b', stroke: '#1e293b' }),
          note(x - 90, up ? -200 : 40, `${t('board.tpl.milestone')} ${i + 1}`, c),
        ];
      });
      return [line, ...marks];
    }
    case 'venn': {
      return [
        make('ellipse', -260, -170, 340, 340, '', { fill: '#93c5fd', stroke: '#2563eb', opacity: 60 }),
        make('ellipse', -80, -170, 340, 340, '', { fill: '#fca5a5', stroke: '#dc2626', opacity: 60 }),
        make('text', -230, -200, 200, 34, t('board.tpl.setA'), { fontSize: 24, align: 'center' }),
        make('text', 30, -200, 200, 34, t('board.tpl.setB'), { fontSize: 24, align: 'center' }),
        make('text', -100, -16, 200, 34, t('board.tpl.both'), { fontSize: 20, align: 'center' }),
      ];
    }
  }
}

/** A complete board JSON for a template, used when creating a whiteboard from a starter. */
export function templateJson(id: TemplateId | 'blank', t: Translate, centerText = ''): string {
  const board: Board = {
    version: 2,
    elements: id === 'blank' ? [] : buildTemplate(id, t, centerText),
    view: { x: 640, y: 380, zoom: id === 'blank' ? 1 : 0.9 },
  };
  return serializeBoard(board);
}
