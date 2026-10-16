/** The owner is the member whose userId equals workspace.ownerId; they hold the ADMIN role. */
export type DocumentWorkspaceRole = 'ADMIN' | 'EDITOR' | 'VIEWER';
export const DOCUMENT_WORKSPACE_ROLES: DocumentWorkspaceRole[] = [
  'ADMIN',
  'EDITOR',
  'VIEWER',
];

export type DocumentWorkspaceStatus = 'ACTIVE' | 'SUSPENDED' | 'READ_ONLY';

export type MemberStatus = 'ACTIVE' | 'SUSPENDED';

export type ArtifactKind = 'FOLDER' | 'DOCUMENT' | 'FILE' | 'TEMPLATE';

export type ArtifactType =
  | 'FOLDER'
  | 'MARKDOWN_DOCUMENT'
  | 'CHECKLIST'
  | 'SPREADSHEET'
  | 'PDF'
  | 'DOCX'
  | 'XLSX'
  | 'PPTX'
  | 'TXT'
  | 'IMAGE'
  | 'VIDEO'
  | 'AUDIO'
  | 'ZIP'
  | 'BINARY_FILE'
  | 'TEMPLATE';

/** Types with editable text content (Markdown notes, checklists, spreadsheets). */
export const TEXT_DOCUMENT_TYPES: ArtifactType[] = ['MARKDOWN_DOCUMENT', 'CHECKLIST', 'SPREADSHEET'];

export type AccessMode = 'READ' | 'WRITE' | 'DELETE' | 'COMMENT' | 'SHARE';

export type ExportFormat = 'MARKDOWN' | 'HTML' | 'TXT' | 'JSON' | 'PDF' | 'DOCX' | 'CSV' | 'XLSX';

export const KIND_BY_TYPE: Record<ArtifactType, ArtifactKind> = {
  FOLDER: 'FOLDER',
  MARKDOWN_DOCUMENT: 'DOCUMENT',
  CHECKLIST: 'DOCUMENT',
  SPREADSHEET: 'DOCUMENT',
  PDF: 'FILE',
  DOCX: 'FILE',
  XLSX: 'FILE',
  PPTX: 'FILE',
  TXT: 'FILE',
  IMAGE: 'FILE',
  VIDEO: 'FILE',
  AUDIO: 'FILE',
  ZIP: 'FILE',
  BINARY_FILE: 'FILE',
  TEMPLATE: 'TEMPLATE',
};
