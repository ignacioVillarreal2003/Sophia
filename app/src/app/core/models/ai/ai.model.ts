/** Shapes of the AI service (api/ai). Field names match the API; enums are the API's own values. */

export type StudyLevel = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';
export type NoteLength = 'SHORT' | 'MEDIUM' | 'LONG';
export type NotesInclude = 'KEY_TERMS' | 'EXAMPLES' | 'SUMMARY' | 'QUESTIONS' | 'TABLES';
export type DeckMode = 'CONCEPTS' | 'FLASHCARDS';
export type MapKind = 'MINDMAP' | 'CONCEPT_MAP';
export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD' | 'MIXED';
export type QuestionType = 'MULTIPLE_CHOICE' | 'TRUE_FALSE' | 'SHORT_ANSWER';
export type Lens =
  | 'HISTORICAL'
  | 'SCIENTIFIC'
  | 'ECONOMIC'
  | 'SOCIAL'
  | 'ETHICAL'
  | 'PRACTICAL'
  | 'CRITICAL'
  | 'EXPERT'
  | 'BEGINNER'
  | 'FUTURE';
export type RewriteAction =
  | 'SIMPLIFY'
  | 'EXPAND'
  | 'SHORTEN'
  | 'FORMAL'
  | 'FRIENDLY'
  | 'FIX'
  | 'BULLETS'
  | 'TRANSLATE'
  | 'ANALOGY'
  | 'STEP_BY_STEP'
  | 'EXAMPLE'
  | 'ELI10'
  | 'SUMMARIZE';
export type ChatMode = 'DOCUMENTS' | 'SOCRATIC' | 'DEBATE';
export type Stance = 'PRO' | 'CON';

export const LENSES: Lens[] = [
  'HISTORICAL',
  'SCIENTIFIC',
  'ECONOMIC',
  'SOCIAL',
  'ETHICAL',
  'PRACTICAL',
  'CRITICAL',
  'EXPERT',
  'BEGINNER',
  'FUTURE',
];

export interface SourceRef {
  artifactId: string;
}

// ---- status and administration ----------------------------------------------------------------------------

export interface AiQuota {
  userUsed: number;
  userLimit: number;
  workspaceUsed: number;
  workspaceLimit: number;
  resetsAt: string;
}

export interface AiStatus {
  enabled: boolean;
  configured: boolean;
  mock: boolean;
  model: string;
  maxSourceChars: number;
  maxSources: number;
  quota: AiQuota;
}

export interface AiSettings {
  enabled: boolean;
  dailyTokensPerUser: number;
  dailyTokensPerWorkspace: number;
  configured: boolean;
  mock: boolean;
  model: string;
  updatedAt: string | null;
  updatedBy: string | null;
}

export interface AiSettingsUpdate {
  enabled: boolean;
  dailyTokensPerUser: number;
  dailyTokensPerWorkspace: number;
}

export interface UsageDay {
  day: string;
  tokens: number;
  requests: number;
}

export interface UsageFeature {
  feature: string;
  tokens: number;
  requests: number;
}

export interface UsageUser {
  userId: string;
  email: string | null;
  tokens: number;
  requests: number;
}

export interface AiUsage {
  days: number;
  tokensToday: number;
  tokensTotal: number;
  requestsTotal: number;
  perDay: UsageDay[];
  perFeature: UsageFeature[];
  topUsers: UsageUser[];
}

// ---- results ----------------------------------------------------------------------------------------------

export interface DeckCard {
  title: string;
  body: string;
  color: string;
}

export interface DeckResult {
  title: string;
  slides: DeckCard[];
}

export interface MapNode {
  id: string;
  label: string;
  parent: string;
}

export interface MapLink {
  from: string;
  to: string;
  label: string;
}

export interface MindMapResult {
  title: string;
  kind: MapKind;
  nodes: MapNode[];
  links: MapLink[];
}

export interface QuizQuestion {
  type: QuestionType;
  question: string;
  options: string[];
  /** MULTIPLE_CHOICE: index of the right option. TRUE_FALSE: 0 when true, 1 when false. SHORT_ANSWER: -1. */
  correctIndex: number;
  answer: string;
  explanation: string;
  source: number;
}

export interface QuizResult {
  title: string;
  questions: QuizQuestion[];
}

export interface GradedAnswer {
  index: number;
  score: number;
  feedback: string;
}

export interface GradeResult {
  results: GradedAnswer[];
}

export interface PlanTask {
  text: string;
  minutes: number;
}

export interface PlanDay {
  date: string;
  focus: string;
  tasks: PlanTask[];
}

export interface StudyPlanResult {
  title: string;
  summary: string;
  days: PlanDay[];
  tips: string[];
  markdown: string;
}

export interface PerspectiveView {
  lens: string;
  summary: string;
  keyPoints: string[];
  question: string;
}

export interface PerspectivesResult {
  title: string;
  overview: string;
  perspectives: PerspectiveView[];
  synthesis: string;
  markdown: string;
}

export interface DebateArgument {
  claim: string;
  support: string;
}

export interface DebateSide {
  thesis: string;
  arguments: DebateArgument[];
  rebuttal: string;
}

export interface DebateResult {
  title: string;
  motion: string;
  pro: DebateSide;
  con: DebateSide;
  verdict: string;
  openQuestions: string[];
  markdown: string;
}

export interface Gap {
  topic: string;
  why: string;
  suggestion: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH';
}

export interface NoteError {
  quote: string;
  issue: string;
  fix: string;
}

export interface GapsResult {
  title: string;
  coverage: number;
  verdict: string;
  covered: string[];
  gaps: Gap[];
  errors: NoteError[];
  nextSteps: string[];
  markdown: string;
}

// ---- conversations ----------------------------------------------------------------------------------------

export interface ConversationSource {
  artifactId: string;
  title: string;
}

export interface ConversationSummary {
  id: string;
  workspaceId: string | null;
  mode: ChatMode;
  title: string;
  topic: string | null;
  stance: Stance | null;
  language: string | null;
  sources: ConversationSource[];
  createdAt: string;
  updatedAt: string;
}

export interface Citation {
  index: number;
  artifactId: string;
  title: string;
}

export interface ChatMessageView {
  id: string;
  role: 'USER' | 'ASSISTANT';
  content: string;
  citations: Citation[];
  createdAt: string;
}

export interface ConversationView {
  conversation: ConversationSummary;
  messages: ChatMessageView[];
}

// ---- requests ---------------------------------------------------------------------------------------------

export interface NotesRequest {
  workspaceId?: string;
  topic?: string;
  level?: StudyLevel;
  length?: NoteLength;
  language?: string;
  include?: NotesInclude[];
  instructions?: string;
  sources?: SourceRef[];
}

export interface RewriteRequest {
  workspaceId?: string;
  text: string;
  action: RewriteAction;
  targetLanguage?: string;
  language?: string;
}

export interface ContinueRequest {
  workspaceId?: string;
  before: string;
  after?: string;
  length?: NoteLength;
  language?: string;
}

export interface ReviewSummaryRequest {
  workspaceId: string;
  sources: SourceRef[];
  weakTopics?: string[];
  language?: string;
}

export interface DeckRequest {
  workspaceId?: string;
  mode: DeckMode;
  topic?: string;
  sources?: SourceRef[];
  count?: number;
  level?: StudyLevel;
  language?: string;
}

export interface MindMapRequest {
  workspaceId?: string;
  kind: MapKind;
  topic?: string;
  sources?: SourceRef[];
  depth?: number;
  language?: string;
}

export interface QuizRequest {
  workspaceId?: string;
  topic?: string;
  sources?: SourceRef[];
  count?: number;
  difficulty?: Difficulty;
  types?: QuestionType[];
  exam?: boolean;
  language?: string;
}

export interface GradeItem {
  question: string;
  reference: string;
  answer: string;
}

export interface GradeRequest {
  workspaceId?: string;
  items: GradeItem[];
  language?: string;
}

export interface StudyPlanRequest {
  workspaceId?: string;
  topic?: string;
  sources?: SourceRef[];
  examDate: string;
  today?: string;
  minutesPerDay?: number;
  daysOff?: number[];
  level?: StudyLevel;
  language?: string;
}

export interface PerspectivesRequest {
  workspaceId?: string;
  topic?: string;
  sources?: SourceRef[];
  lenses?: Lens[];
  customLenses?: string[];
  level?: StudyLevel;
  language?: string;
}

export interface DebateRequest {
  workspaceId?: string;
  motion: string;
  sources?: SourceRef[];
  level?: StudyLevel;
  language?: string;
}

export interface GapsRequest {
  workspaceId?: string;
  notes: SourceRef[];
  reference?: SourceRef[];
  topic?: string;
  level?: StudyLevel;
  language?: string;
}

export interface CreateConversationRequest {
  workspaceId?: string;
  mode: ChatMode;
  title?: string;
  topic?: string;
  stance?: Stance;
  sources?: SourceRef[];
  language?: string;
}

export interface UpdateConversationRequest {
  title?: string;
  sources?: SourceRef[];
}

// ---- conversions ------------------------------------------------------------------------------------------

export type DiagramKind = 'FLOWCHART' | 'TIMELINE' | 'CYCLE';
export type OcrMode = 'TRANSCRIBE' | 'NOTES';
export type DiagramShape = 'START' | 'END' | 'PROCESS' | 'DECISION' | 'EVENT';

export interface TableRow {
  cells: string[];
}

export interface TableResult {
  title: string;
  headers: string[];
  rows: TableRow[];
}

export interface DiagramNode {
  id: string;
  label: string;
  shape: DiagramShape;
  detail: string;
}

export interface DiagramEdge {
  from: string;
  to: string;
  label: string;
}

export interface DiagramResult {
  title: string;
  kind: DiagramKind;
  nodes: DiagramNode[];
  edges: DiagramEdge[];
}

export interface TagsResult {
  title: string;
  summary: string;
  tags: string[];
}

export interface TableRequest {
  workspaceId?: string;
  text?: string;
  sources?: SourceRef[];
  instructions?: string;
  language?: string;
}

export interface DiagramRequest {
  workspaceId?: string;
  kind: DiagramKind;
  topic?: string;
  text?: string;
  sources?: SourceRef[];
  language?: string;
}

export interface TagsRequest {
  workspaceId?: string;
  text: string;
  language?: string;
}

export interface ListenRequest {
  workspaceId?: string;
  text: string;
  language?: string;
}
