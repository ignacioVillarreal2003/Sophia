import { BoardElement, ElementType, emptyBoard, newId, serializeBoard } from '../../document-workspace/whiteboard/board.model';
import { DiagramNode, DiagramResult } from '@core/models/ai/ai.model';

/**
 * Lays a generated diagram out on a whiteboard. Flowcharts are drawn in layers from top to bottom, timelines along a
 * line and cycles around a circle; every connector is an arrow glued to its shapes, so the board stays editable.
 */

const SIZE = {
  START: { w: 190, h: 70 },
  END: { w: 190, h: 70 },
  PROCESS: { w: 210, h: 80 },
  DECISION: { w: 240, h: 140 },
  EVENT: { w: 200, h: 90 },
} as const;
const LAYER_GAP = 90;
const COLUMN_GAP = 70;

const STYLE: Record<string, { type: ElementType; fill: string; stroke: string; round?: boolean }> = {
  START: { type: 'ellipse', fill: '#bbf7d0', stroke: '#16a34a' },
  END: { type: 'ellipse', fill: '#e2e8f0', stroke: '#475569' },
  PROCESS: { type: 'box', fill: '#bfdbfe', stroke: '#2563eb', round: true },
  DECISION: { type: 'diamond', fill: '#fde68a', stroke: '#d97706' },
  EVENT: { type: 'box', fill: '#ddd6fe', stroke: '#7c3aed', round: true },
};

function shapeOf(node: DiagramNode, x: number, y: number, id: string): BoardElement {
  const look = STYLE[node.shape] ?? STYLE['PROCESS'];
  const size = SIZE[node.shape as keyof typeof SIZE] ?? SIZE.PROCESS;
  return {
    id, type: look.type, x: x - size.w / 2, y: y - size.h / 2, w: size.w, h: size.h, text: node.label, color: 'blue',
    fill: look.fill, stroke: look.stroke, round: look.round, fontSize: 16,
  };
}

function connector(from: string, to: string, label: string, route: 'straight' | 'curve' | 'elbow'): BoardElement {
  return { id: newId(), type: 'arrow', x: 0, y: 0, w: 0, h: 0, text: label, color: 'slate', from, to, route, endHead: 'arrow' };
}

// ---- flowchart -------------------------------------------------------------------------------------------------

/** Edges that point back to a step already on the path (loops): found by depth-first search, drawn as curves. */
function backEdges(diagram: DiagramResult): Set<string> {
  const out = new Map<string, string[]>();
  diagram.nodes.forEach((n) => out.set(n.id, []));
  diagram.edges.forEach((e) => out.get(e.from)?.push(e.to));
  const incoming = new Set(diagram.edges.map((e) => e.to));
  const roots = diagram.nodes.filter((n) => !incoming.has(n.id)).map((n) => n.id);
  const starts = [...roots, ...diagram.nodes.map((n) => n.id)];
  const state = new Map<string, 'open' | 'done'>();
  const back = new Set<string>();
  const visit = (id: string): void => {
    state.set(id, 'open');
    for (const next of out.get(id) ?? []) {
      if (state.get(next) === 'open') {
        back.add(`${id}>${next}`);
      } else if (!state.has(next)) {
        visit(next);
      }
    }
    state.set(id, 'done');
  };
  starts.forEach((id) => !state.has(id) && visit(id));
  return back;
}

function flowchart(diagram: DiagramResult): BoardElement[] {
  const back = backEdges(diagram);
  const forward = diagram.edges.filter((e) => !back.has(`${e.from}>${e.to}`));
  const layer = new Map<string, number>(diagram.nodes.map((n) => [n.id, 0]));
  // Longest path over the acyclic edges: a step sits below everything that leads to it.
  for (let pass = 0; pass < diagram.nodes.length; pass++) {
    let changed = false;
    for (const e of forward) {
      if ((layer.get(e.to) ?? 0) < (layer.get(e.from) ?? 0) + 1) {
        layer.set(e.to, (layer.get(e.from) ?? 0) + 1);
        changed = true;
      }
    }
    if (!changed) {
      break;
    }
  }
  const layers: DiagramNode[][] = [];
  diagram.nodes.forEach((n) => (layers[layer.get(n.id) ?? 0] ??= []).push(n));

  const x = new Map<string, number>();
  const heights: number[] = [];
  const placed: { node: DiagramNode; x: number; layer: number }[] = [];
  layers.forEach((nodes, depth) => {
    if (!nodes) {
      heights[depth] = 0;
      return;
    }
    // Order by where the parents are, so lines cross less.
    const key = (n: DiagramNode): number => {
      const parents = forward.filter((e) => e.to === n.id).map((e) => x.get(e.from)).filter((v): v is number => v !== undefined);
      return parents.length ? parents.reduce((a, b) => a + b, 0) / parents.length : 0;
    };
    const ordered = depth === 0 ? nodes : [...nodes].sort((a, b) => key(a) - key(b));
    const widths = ordered.map((n) => (SIZE[n.shape as keyof typeof SIZE] ?? SIZE.PROCESS).w);
    const total = widths.reduce((a, b) => a + b, 0) + COLUMN_GAP * (ordered.length - 1);
    let cursor = -total / 2;
    ordered.forEach((n, i) => {
      const cx = cursor + widths[i] / 2;
      x.set(n.id, cx);
      placed.push({ node: n, x: cx, layer: depth });
      cursor += widths[i] + COLUMN_GAP;
    });
    heights[depth] = Math.max(...ordered.map((n) => (SIZE[n.shape as keyof typeof SIZE] ?? SIZE.PROCESS).h));
  });
  const top: number[] = [];
  let y = 0;
  heights.forEach((h, depth) => {
    top[depth] = y + h / 2;
    y += h + LAYER_GAP;
  });

  const ids = new Map<string, string>();
  const shapes = placed.map((p) => {
    const id = newId();
    ids.set(p.node.id, id);
    return shapeOf(p.node, p.x, top[p.layer], id);
  });
  const arrows = diagram.edges.map((e) => {
    const isBack = back.has(`${e.from}>${e.to}`);
    const branching = diagram.nodes.find((n) => n.id === e.from)?.shape === 'DECISION';
    return connector(ids.get(e.from)!, ids.get(e.to)!, e.label, isBack ? 'curve' : branching ? 'elbow' : 'straight');
  });
  return [...arrows, ...shapes];
}

// ---- timeline --------------------------------------------------------------------------------------------------

function timeline(diagram: DiagramResult): BoardElement[] {
  const step = SIZE.EVENT.w + 50;
  const total = (diagram.nodes.length - 1) * step;
  const elements: BoardElement[] = [];
  const line: BoardElement = {
    id: newId(), type: 'line', x: -step / 2, y: 0, w: total + step, h: 0, text: '', color: 'slate', x2: total + step / 2, y2: 0,
    strokeWidth: 3, stroke: '#64748b',
  };
  elements.push(line);
  diagram.nodes.forEach((node, i) => {
    const cx = i * step;
    const above = i % 2 === 0;
    const markerId = newId();
    const boxId = newId();
    elements.push({
      id: markerId, type: 'ellipse', x: cx - 13, y: -13, w: 26, h: 26, text: '', color: 'violet', fill: '#7c3aed', stroke: '#5b21b6',
    });
    const by = above ? -(SIZE.EVENT.h + 70) : 70;
    elements.push({
      ...shapeOf(node, cx, by + SIZE.EVENT.h / 2, boxId),
    });
    elements.push({
      id: newId(), type: 'arrow', x: 0, y: 0, w: 0, h: 0, text: '', color: 'slate', from: markerId, to: boxId, route: 'straight', endHead: 'none',
    });
    if (node.detail) {
      elements.push({
        id: newId(), type: 'text', x: cx - 80, y: above ? 24 : -52, w: 160, h: 28, text: node.detail, color: 'slate', align: 'center',
        fontSize: 16, stroke: '#5b21b6',
      });
    }
  });
  return elements;
}

// ---- cycle -----------------------------------------------------------------------------------------------------

function cycle(diagram: DiagramResult): BoardElement[] {
  const n = diagram.nodes.length;
  const radius = Math.max(260, (n * (SIZE.PROCESS.w + 40)) / (2 * Math.PI));
  const ids = new Map<string, string>();
  const shapes = diagram.nodes.map((node, i) => {
    const angle = (i / n) * Math.PI * 2 - Math.PI / 2;
    const id = newId();
    ids.set(node.id, id);
    return shapeOf(node, Math.cos(angle) * radius * 1.3, Math.sin(angle) * radius, id);
  });
  const arrows = diagram.edges.map((e) => connector(ids.get(e.from)!, ids.get(e.to)!, e.label, 'curve'));
  return [...arrows, ...shapes];
}

export function diagramElements(diagram: DiagramResult): BoardElement[] {
  switch (diagram.kind) {
    case 'TIMELINE':
      return timeline(diagram);
    case 'CYCLE':
      return cycle(diagram);
    default:
      return flowchart(diagram);
  }
}

export function diagramContent(diagram: DiagramResult): string {
  const board = emptyBoard();
  board.elements = diagramElements(diagram);
  return serializeBoard(board);
}
