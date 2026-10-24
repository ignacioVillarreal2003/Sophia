import { GradeItem, GradedAnswer, QuizQuestion } from '@core/models/ai/ai.model';

/** What the student answered: an option for choice questions, text for short ones. */
export interface QuizAnswer {
  choice: number | null;
  text: string;
}

export const EMPTY_ANSWER: QuizAnswer = { choice: null, text: '' };

export interface QuestionOutcome {
  /** 0, 0.5 or 1 */
  score: number;
  answered: boolean;
  feedback: string;
}

export interface QuizOutcome {
  perQuestion: QuestionOutcome[];
  points: number;
  total: number;
  percent: number;
  /** The questions that were not fully right, as short topic lines for a review summary. */
  missed: string[];
}

/** Choice questions are marked here; short answers need the model, so they come back unmarked (null). */
export function markChoice(question: QuizQuestion, answer: QuizAnswer): number | null {
  if (question.type === 'SHORT_ANSWER') {
    return null;
  }
  return answer.choice !== null && answer.choice === question.correctIndex ? 1 : 0;
}

export function isAnswered(question: QuizQuestion, answer: QuizAnswer): boolean {
  return question.type === 'SHORT_ANSWER' ? answer.text.trim().length > 0 : answer.choice !== null;
}

/** The short answers that have something to grade, with their position among all the questions. */
export function itemsToGrade(
  questions: QuizQuestion[],
  answers: QuizAnswer[],
): { index: number; item: GradeItem }[] {
  const out: { index: number; item: GradeItem }[] = [];
  questions.forEach((question, index) => {
    const answer = answers[index] ?? EMPTY_ANSWER;
    if (question.type === 'SHORT_ANSWER' && answer.text.trim()) {
      out.push({ index, item: { question: question.question, reference: question.answer, answer: answer.text.trim() } });
    }
  });
  return out;
}

/**
 * Puts the marks together. `graded` maps a question index to the model's grade for it (short answers only); a short
 * answer without a grade scores 0, so a failed grading call never inflates the result.
 */
export function outcome(
  questions: QuizQuestion[],
  answers: QuizAnswer[],
  graded: Map<number, GradedAnswer>,
): QuizOutcome {
  const perQuestion = questions.map((question, index): QuestionOutcome => {
    const answer = answers[index] ?? EMPTY_ANSWER;
    const answered = isAnswered(question, answer);
    if (question.type === 'SHORT_ANSWER') {
      const grade = graded.get(index);
      return { score: answered ? (grade?.score ?? 0) : 0, answered, feedback: grade?.feedback ?? '' };
    }
    return { score: answered ? (markChoice(question, answer) ?? 0) : 0, answered, feedback: '' };
  });
  const points = perQuestion.reduce((sum, q) => sum + q.score, 0);
  const total = questions.length;
  const missed = questions
    .filter((_, i) => perQuestion[i].score < 1)
    .map((q) => q.question.replace(/\s+/g, ' ').trim().slice(0, 180));
  return { perQuestion, points, total, percent: total === 0 ? 0 : Math.round((points / total) * 100), missed };
}

/** Minutes a mock exam gets by default: a minute and a half per question, rounded up to a multiple of 5. */
export function defaultExamMinutes(questionCount: number): number {
  return Math.max(5, Math.ceil((questionCount * 1.5) / 5) * 5);
}

export function formatClock(totalSeconds: number): string {
  const s = Math.max(0, Math.floor(totalSeconds));
  const hours = Math.floor(s / 3600);
  const minutes = Math.floor((s % 3600) / 60);
  const seconds = s % 60;
  const pad = (n: number) => String(n).padStart(2, '0');
  return hours > 0 ? `${hours}:${pad(minutes)}:${pad(seconds)}` : `${pad(minutes)}:${pad(seconds)}`;
}

/** The quiz as a note: questions first, answers and explanations after, so it can be used on paper. */
export function quizMarkdown(
  title: string,
  questions: QuizQuestion[],
  labels: { answers: string; true: string; false: string },
): string {
  const lines: string[] = [`# ${title}`, ''];
  questions.forEach((q, i) => {
    lines.push(`${i + 1}. ${q.question}`);
    if (q.type === 'MULTIPLE_CHOICE') {
      q.options.forEach((option, j) => lines.push(`   - ${String.fromCharCode(97 + j)}) ${option}`));
    } else if (q.type === 'TRUE_FALSE') {
      lines.push(`   - ${labels.true} / ${labels.false}`);
    }
    lines.push('');
  });
  lines.push('---', '', `## ${labels.answers}`, '');
  questions.forEach((q, i) => {
    const answer =
      q.type === 'MULTIPLE_CHOICE'
        ? `${String.fromCharCode(97 + q.correctIndex)}) ${q.answer}`
        : q.type === 'TRUE_FALSE'
          ? q.correctIndex === 0
            ? labels.true
            : labels.false
          : q.answer;
    lines.push(`${i + 1}. **${answer}**${q.explanation ? ` - ${q.explanation}` : ''}`);
  });
  return lines.join('\n') + '\n';
}
