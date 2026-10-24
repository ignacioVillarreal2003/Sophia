import { Board, BoardElement, emptyBoard, newId, serializeBoard } from '../../document-workspace/whiteboard/board.model';
import { MapLink, MapNode, MindMapResult } from '@core/models/ai/ai.model';

/**
 * Turns a generated map into a whiteboard. The result is plain board JSON, so once created it is an ordinary
 * whiteboard the person can keep editing: nodes are boxes, links are arrows glued to them.
 */

const ROW = 92;
const COLUMN = 310;
const NODE_HEIGHT = 64;
const ROOT_W = 230;
const ROOT_H = 116;

/** Fill/stroke per top-level branch, so a branch and everything below it share a color. */
const BRANCH_COLORS: { fill: string; stroke: string }[] = [
  { fill: '#bfdbfe', stroke: '#2563eb' },
  { fill: '#bbf7d0', stroke: '#16a34a' },
  { fill: '#fde68a', stroke: '#d97706' },
  { fill: '#fecdd3', stroke: '#e11d48' },
  { fill: '#ddd6fe', stroke: '#7c3aed' },
  { fill: '#a5f3fc', stroke: '#0891b2' },
  { fill: '#fbcfe8', stroke: '#db2777' },
];

function nodeWidth(label: string): number {
  return Math.min(250, Math.max(130, 56 + label.length * 7));
}

function box(node: MapNode, x: number, y: number, color: { fill: string; stroke: string }, id: string): BoardElement {
  const w = nodeWidth(node.label);
  return {
    id, type: 'box', x: x - w / 2, y: y - NODE_HEIGHT / 2, w, h: NODE_HEIGHT, text: node.label, color: 'blue',
    fill: color.fill, stroke: color.stroke, round: true, fontSize: 16,
  };
}

function arrow(from: string, to: string, text = '', route: BoardElement['route'] = 'curve'): BoardElement {
  return {
    id: newId(), type: 'arrow', x: 0, y: 0, w: 0, h: 0, text, color: 'slate', from, to, route, endHead: route === 'curve' ? 'none' : 'arrow',
  };
}

interface Placed {
  node: MapNode;
  x: number;
  y: number;
  depth: number;
  branch: number;
}

/** Mind map: the root in the middle, branches alternating to the right and to the left, leaves spaced evenly. */
function mindMapElements(map: MindMapResult): BoardElement[] {
  const byParent = new Map<string, MapNode[]>();
  let root: MapNode | undefined;
  for (const node of map.nodes) {
    if (!node.parent) {
      root ??= node;
      continue;
    }
    const siblings = byParent.get(node.parent) ?? [];
    siblings.push(node);
    byParent.set(node.parent, siblings);
  }
  if (!root) {
    return [];
  }

  const leaves = (node: MapNode): number => {
    const kids = byParent.get(node.id) ?? [];
    return kids.length === 0 ? 1 : kids.reduce((sum, k) => sum + leaves(k), 0);
  };

  const placed: Placed[] = [];
  const place = (node: MapNode, side: 1 | -1, depth: number, top: number, branch: number): void => {
    const kids = byParent.get(node.id) ?? [];
    if (kids.length === 0) {
      placed.push({ node, x: side * depth * COLUMN, y: top + ROW / 2, depth, branch });
      return;
    }
    let cursor = top;
    const firstChild = placed.length;
    for (const kid of kids) {
      place(kid, side, depth + 1, cursor, branch);
      cursor += leaves(kid) * ROW;
    }
    const children = placed.slice(firstChild).filter((p) => p.depth === depth + 1);
    const y = (children[0].y + children[children.length - 1].y) / 2;
    placed.push({ node, x: side * depth * COLUMN, y, depth, branch });
  };

  const branches = byParent.get(root.id) ?? [];
  const right = branches.filter((_, i) => i % 2 === 0);
  const left = branches.filter((_, i) => i % 2 === 1);
  const layoutSide = (group: MapNode[], side: 1 | -1): void => {
    const total = group.reduce((sum, b) => sum + leaves(b), 0) * ROW;
    let cursor = -total / 2;
    for (const branch of group) {
      place(branch, side, 1, cursor, branches.indexOf(branch));
      cursor += leaves(branch) * ROW;
    }
  };
  layoutSide(right, 1);
  layoutSide(left, -1);

  const ids = new Map<string, string>();
  const rootId = newId();
  ids.set(root.id, rootId);
  const elements: BoardElement[] = [];
  const edges: BoardElement[] = [];
  const rootElement: BoardElement = {
    id: rootId, type: 'ellipse', x: -ROOT_W / 2, y: -ROOT_H / 2, w: ROOT_W, h: ROOT_H, text: root.label, color: 'violet',
    fill: '#ddd6fe', stroke: '#7c3aed', strokeWidth: 3, fontSize: 20,
  };
  for (const p of placed) {
    const id = newId();
    ids.set(p.node.id, id);
    elements.push(box(p.node, p.x, p.y, BRANCH_COLORS[p.branch % BRANCH_COLORS.length], id));
  }
  for (const p of placed) {
    edges.push(arrow(ids.get(p.node.parent)!, ids.get(p.node.id)!));
  }
  return [...edges, rootElement, ...elements];
}

/** Concept map: the best-connected concept in the middle, the rest on rings in breadth-first order. */
function conceptMapElements(map: MindMapResult): BoardElement[] {
  const degree = new Map<string, number>();
  const neighbours = new Map<string, string[]>();
  for (const node of map.nodes) {
    degree.set(node.id, 0);
    neighbours.set(node.id, []);
  }
  for (const link of map.links) {
    degree.set(link.from, (degree.get(link.from) ?? 0) + 1);
    degree.set(link.to, (degree.get(link.to) ?? 0) + 1);
    neighbours.get(link.from)?.push(link.to);
    neighbours.get(link.to)?.push(link.from);
  }
  const byId = new Map(map.nodes.map((n) => [n.id, n]));
  const start = [...map.nodes].sort((a, b) => (degree.get(b.id) ?? 0) - (degree.get(a.id) ?? 0))[0];
  const order: MapNode[] = [];
  const seen = new Set<string>();
  const queue = [start.id];
  seen.add(start.id);
  while (queue.length > 0) {
    const id = queue.shift()!;
    order.push(byId.get(id)!);
    for (const next of neighbours.get(id) ?? []) {
      if (!seen.has(next)) {
        seen.add(next);
        queue.push(next);
      }
    }
  }
  for (const node of map.nodes) {
    if (!seen.has(node.id)) {
      order.push(node); // unconnected leftovers still get a place
    }
  }

  const ids = new Map<string, string>();
  const elements: BoardElement[] = [];
  let ring = 0;
  let slot = 0;
  let capacity = 1;
  order.forEach((node, i) => {
    if (i > 0 && slot === capacity) {
      ring += 1;
      slot = 0;
      capacity = ring * 6;
    }
    const radius = ring * 330;
    const angle = ring === 0 ? 0 : (slot / capacity) * Math.PI * 2 - Math.PI / 2;
    const x = Math.cos(angle) * radius * 1.35; // wider than tall, since the boxes are
    const y = Math.sin(angle) * radius;
    const id = newId();
    ids.set(node.id, id);
    elements.push(box(node, x, y, BRANCH_COLORS[ring % BRANCH_COLORS.length], id));
    slot += 1;
  });
  const edges = map.links.map((link: MapLink) => arrow(ids.get(link.from)!, ids.get(link.to)!, link.label, 'straight'));
  return [...edges, ...elements];
}

export function boardFromMap(map: MindMapResult): Board {
  const board = emptyBoard();
  board.elements = map.kind === 'CONCEPT_MAP' ? conceptMapElements(map) : mindMapElements(map);
  board.grid = false;
  return board;
}

export function boardContent(map: MindMapResult): string {
  return serializeBoard(boardFromMap(map));
}
