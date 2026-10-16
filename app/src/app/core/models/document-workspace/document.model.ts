/** Text content (Markdown or workbook JSON) of a document at its current version. */
export interface DocumentContent {
  readonly documentId: string;
  readonly type: string;
  readonly version: number;
  readonly content: string;
  readonly updatedAt: string;
}

/** One saved snapshot of a document's history. */
export interface DocumentVersion {
  readonly id: string;
  readonly documentId: string;
  readonly version: number;
  readonly name: string | null;
  readonly createdBy: string;
  readonly savedAt: string;
  readonly sizeChars: number;
  /** Only present when a single version is fetched. */
  readonly content?: string;
}
