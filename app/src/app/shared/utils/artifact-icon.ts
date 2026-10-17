import { ArtifactType } from '@core/models/document-workspace';
import { IconName } from '../ui/icon/icons';

const ICON_BY_TYPE: Partial<Record<ArtifactType, IconName>> = {
  FOLDER: 'folder',
  MARKDOWN_DOCUMENT: 'file-text',
  CHECKLIST: 'check-circle',
  PDF: 'file',
  DOCX: 'file-text',
  XLSX: 'table',
  PPTX: 'file',
  TXT: 'file-text',
  IMAGE: 'image',
  VIDEO: 'file',
  AUDIO: 'file',
  ZIP: 'file',
  BINARY_FILE: 'file',
  SPREADSHEET: 'table',
  PRESENTATION: 'layout-cards',
  WHITEBOARD: 'pencil',
};

export function artifactIcon(type: ArtifactType): IconName {
  return ICON_BY_TYPE[type] ?? 'file';
}
