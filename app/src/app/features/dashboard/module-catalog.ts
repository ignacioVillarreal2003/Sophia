import {IconName} from '@shared/ui/icon/icons';

export interface ModuleDefinition {
  readonly slug: string;
  readonly label: string;
  readonly description: string;
  readonly icon: IconName;
  /**
   * Absolute path to navigate to for this module. When present, the module
   * is its own top-level app and this overrides the
   * default `/document-workspace/modules/{slug}` nesting.
   */
  readonly route?: string;
}

/**
 * The modules the platform actually ships. Unless a module declares its own
 * `route`, the slug is the route segment under `/document-workspace/modules/`,
 * so adding one here is only half the job — it also needs an entry in
 * `modules.routes.ts` or it lands on the 404 catch-all.
 */
export const MODULES: ModuleDefinition[] = [
  {
    slug: 'document-workspace',
    label: 'modules.documentWorkspace.label',
    description: 'modules.documentWorkspace.description',
    icon: 'book',
  },
];

export function findModule(slug: string): ModuleDefinition | undefined {
  return MODULES.find((m) => m.slug === slug);
}

/**
 * The modules to show on the home screen for a user.
 *
 * `hidden` is a deny list, so a module added to the catalogue later is visible
 * to everyone by default instead of staying hidden until each user opts in. A
 * slug in `hidden` that no longer exists in the catalogue is simply ignored,
 * which is what lets a module be removed without migrating anyone's preferences.
 */
export function visibleModules(hidden: readonly string[] | null | undefined): ModuleDefinition[] {
  const denied = new Set(hidden ?? []);
  return MODULES.filter((m) => !denied.has(m.slug));
}
