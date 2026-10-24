import { describe, expect, it } from 'vitest';
import { MindMapResult, QuizQuestion } from '@core/models/ai/ai.model';
import { boardFromMap } from './board-builder';
import { aiErrorMessage } from './errors';
import {
  defaultExamMinutes,
  formatClock,
  itemsToGrade,
  markChoice,
  outcome,
  quizMarkdown,
} from './quiz-session';
import { SseParser } from './sse';

describe('SseParser', () => {
  it('reads events that arrive in one piece', () => {
    const events = new SseParser().push('event:meta\ndata:{"a":1}\n\nevent:delta\ndata:{"t":"hola"}\n\n');
    expect(events).toEqual([
      { name: 'meta', data: { a: 1 } },
      { name: 'delta', data: { t: 'hola' } },
    ]);
  });

  it('keeps an event that is cut between chunks until it is complete', () => {
    const parser = new SseParser();
    expect(parser.push('event:delta\nda')).toEqual([]);
    expect(parser.push('ta:{"t":"ab')).toEqual([]);
    expect(parser.push('c"}\n\nevent:done\ndata:{}\n\n')).toEqual([
      { name: 'delta', data: { t: 'abc' } },
      { name: 'done', data: {} },
    ]);
  });

  it('ignores keep-alive comments, windows line endings and broken data', () => {
    const events = new SseParser().push(':ping\n\nevent:delta\r\ndata:{"t":"x"}\r\n\r\nevent:delta\ndata:not json\n\n');
    expect(events).toEqual([{ name: 'delta', data: { t: 'x' } }]);
  });

  it('keeps text with accents and newlines inside JSON intact', () => {
    const events = new SseParser().push('event:delta\ndata:{"t":"línea 1\\nlínea 2 ñ"}\n\n');
    expect((events[0].data as { t: string }).t).toBe('línea 1\nlínea 2 ñ');
  });
});

describe('boardFromMap', () => {
  const mind: MindMapResult = {
    title: 'Célula',
    kind: 'MINDMAP',
    nodes: [
      { id: 'r', label: 'Célula', parent: '' },
      { id: 'a', label: 'Núcleo', parent: 'r' },
      { id: 'b', label: 'Membrana', parent: 'r' },
      { id: 'c', label: 'Citoplasma', parent: 'r' },
      { id: 'a1', label: 'ADN', parent: 'a' },
      { id: 'a2', label: 'Nucléolo', parent: 'a' },
      { id: 'b1', label: 'Fosfolípidos', parent: 'b' },
    ],
    links: [],
  };

  it('puts every node on the board with an arrow from each parent', () => {
    const board = boardFromMap(mind);
    const shapes = board.elements.filter((e) => e.type !== 'arrow');
    const arrows = board.elements.filter((e) => e.type === 'arrow');
    expect(shapes).toHaveLength(7);
    expect(arrows).toHaveLength(6);
    const ids = new Set(shapes.map((e) => e.id));
    expect(arrows.every((a) => ids.has(a.from!) && ids.has(a.to!))).toBe(true);
  });

  it('never lets two nodes overlap', () => {
    const wide: MindMapResult = {
      title: 't',
      kind: 'MINDMAP',
      nodes: [
        { id: 'r', label: 'Raíz', parent: '' },
        ...Array.from({ length: 6 }, (_, i) => ({ id: `b${i}`, label: `Rama número ${i}`, parent: 'r' })),
        ...Array.from({ length: 6 }, (_, i) =>
          Array.from({ length: 5 }, (_, j) => ({ id: `b${i}-${j}`, label: `Detalle ${i}.${j} más largo`, parent: `b${i}` })),
        ).flat(),
      ],
      links: [],
    };
    const shapes = boardFromMap(wide).elements.filter((e) => e.type !== 'arrow');
    for (let i = 0; i < shapes.length; i++) {
      for (let j = i + 1; j < shapes.length; j++) {
        const a = shapes[i];
        const b = shapes[j];
        const overlap = a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h;
        expect(overlap, `${a.text} / ${b.text}`).toBe(false);
      }
    }
  });

  it('splits the main branches to the right and the left of the root', () => {
    const board = boardFromMap(mind);
    const at = (text: string) => board.elements.find((e) => e.text === text)!;
    expect(at('Núcleo').x).toBeGreaterThan(at('Célula').x);
    expect(at('Membrana').x).toBeLessThan(at('Célula').x);
    expect(at('ADN').x).toBeGreaterThan(at('Núcleo').x);
  });

  it('lays a concept map around its best connected concept, with the link texts', () => {
    const concept: MindMapResult = {
      title: 't',
      kind: 'CONCEPT_MAP',
      nodes: ['a', 'b', 'c', 'd'].map((id) => ({ id, label: id.toUpperCase(), parent: '' })),
      links: [
        { from: 'a', to: 'b', label: 'causa' },
        { from: 'a', to: 'c', label: 'usa' },
        { from: 'a', to: 'd', label: 'incluye' },
      ],
    };
    const board = boardFromMap(concept);
    const center = board.elements.find((e) => e.text === 'A')!;
    expect(Math.abs(center.x + center.w / 2)).toBeLessThan(1);
    expect(board.elements.filter((e) => e.type === 'arrow').map((e) => e.text)).toEqual(['causa', 'usa', 'incluye']);
  });

  it('places concepts without links too', () => {
    const board = boardFromMap({
      title: 't', kind: 'CONCEPT_MAP',
      nodes: [{ id: 'a', label: 'A', parent: '' }, { id: 'b', label: 'B', parent: '' }, { id: 'z', label: 'Z', parent: '' }],
      links: [{ from: 'a', to: 'b', label: 'x' }],
    });
    expect(board.elements.filter((e) => e.type !== 'arrow')).toHaveLength(3);
  });
});

describe('quiz session', () => {
  const questions: QuizQuestion[] = [
    { type: 'MULTIPLE_CHOICE', question: 'Capital de Francia', options: ['Roma', 'París'], correctIndex: 1, answer: 'París', explanation: '', source: 0 },
    { type: 'TRUE_FALSE', question: 'El sol es una estrella', options: [], correctIndex: 0, answer: 'true', explanation: 'Sí', source: 0 },
    { type: 'SHORT_ANSWER', question: 'Define mitosis', options: [], correctIndex: -1, answer: 'División celular', explanation: '', source: 0 },
  ];

  it('marks choice questions and leaves short answers to the model', () => {
    expect(markChoice(questions[0], { choice: 1, text: '' })).toBe(1);
    expect(markChoice(questions[0], { choice: 0, text: '' })).toBe(0);
    expect(markChoice(questions[0], { choice: null, text: '' })).toBe(0);
    expect(markChoice(questions[2], { choice: null, text: 'x' })).toBeNull();
  });

  it('sends only answered short answers for grading', () => {
    const answers = [{ choice: 1, text: '' }, { choice: 0, text: '' }, { choice: null, text: '  se divide  ' }];
    expect(itemsToGrade(questions, answers)).toEqual([
      { index: 2, item: { question: 'Define mitosis', reference: 'División celular', answer: 'se divide' } },
    ]);
    expect(itemsToGrade(questions, [{ choice: 1, text: '' }, { choice: 0, text: '' }, { choice: null, text: '   ' }])).toEqual([]);
  });

  it('adds up points, percentages and the topics that were missed', () => {
    const answers = [{ choice: 0, text: '' }, { choice: 0, text: '' }, { choice: null, text: 'algo' }];
    const graded = new Map([[2, { index: 2, score: 0.5, feedback: 'Falta detalle' }]]);
    const result = outcome(questions, answers, graded);
    expect(result.points).toBe(1.5);
    expect(result.percent).toBe(50);
    expect(result.missed).toEqual(['Capital de Francia', 'Define mitosis']);
    expect(result.perQuestion[2].feedback).toBe('Falta detalle');
  });

  it('scores an ungraded or blank short answer as zero', () => {
    const answers = [{ choice: 1, text: '' }, { choice: 0, text: '' }, { choice: null, text: 'respuesta' }];
    expect(outcome(questions, answers, new Map()).points).toBe(2);
    expect(outcome(questions, [{ choice: null, text: '' }, { choice: null, text: '' }, { choice: null, text: '' }], new Map()).percent).toBe(0);
  });

  it('gives an exam a minute and a half a question, in steps of five', () => {
    expect(defaultExamMinutes(10)).toBe(15);
    expect(defaultExamMinutes(20)).toBe(30);
    expect(defaultExamMinutes(1)).toBe(5);
  });

  it('formats the countdown', () => {
    expect(formatClock(0)).toBe('00:00');
    expect(formatClock(75)).toBe('01:15');
    expect(formatClock(3725)).toBe('1:02:05');
    expect(formatClock(-4)).toBe('00:00');
  });

  it('writes the quiz with the answers after the questions', () => {
    const md = quizMarkdown('Prueba', questions, { answers: 'Respuestas', true: 'Verdadero', false: 'Falso' });
    expect(md.indexOf('1. Capital de Francia')).toBeLessThan(md.indexOf('## Respuestas'));
    expect(md).toContain('   - b) París');
    expect(md).toContain('2. **Verdadero** - Sí');
    expect(md).toContain('3. **División celular**');
  });
});

describe('aiErrorMessage', () => {
  const t = (key: string) => `[${key}]`;

  it('uses the wording of a known code', () => {
    expect(aiErrorMessage({ code: 'QUOTA_EXCEEDED', message: 'x', status: 429 }, t)).toBe('[ai.error.QUOTA_EXCEEDED]');
  });

  it('shows validation messages from the server as they are', () => {
    expect(aiErrorMessage({ code: 'BAD_REQUEST', message: 'Choose at most 12 documents', status: 400 }, t)).toBe(
      'Choose at most 12 documents',
    );
  });

  it('falls back to the generic text', () => {
    expect(aiErrorMessage({ code: 'WHATEVER', message: 'boom', status: 500 }, t)).toBe('[ai.error.GENERATION_FAILED]');
    expect(aiErrorMessage(null, t)).toBe('[ai.error.GENERATION_FAILED]');
  });
});
