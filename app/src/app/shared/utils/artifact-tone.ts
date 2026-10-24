import { ArtifactType } from '@core/models/document-workspace';

/** The color a kind of thing is drawn in: folders warm, documents the accent, sheets green, boards violet, files blue. */
export function artifactTone(type: ArtifactType): string {
  switch (type) {
    case 'FOLDER':
      return '#c47f17';
    case 'SPREADSHEET':
      return '#12805c';
    case 'PRESENTATION':
      return '#7a4dd8';
    case 'WHITEBOARD':
      return '#0e7c9a';
    case 'MARKDOWN_DOCUMENT':
    case 'CHECKLIST':
      return 'var(--accent)';
    default:
      return '#3b6fd4';
  }
}
