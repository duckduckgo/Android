/**
 * Implement this tooltipHandler for anything that should be able
 * to render a line-item in the DataWebTooltip Tooltip
 */
interface TooltipItemRenderer {
  id(): string;
  labelMedium(subtype: string): string;
  label?(subtype: string): string | null | undefined;
  labelSmall?(subtype: string): string | null | undefined;
}

type PosFn = () => { x: number; y: number; height: number; width: number; }
type CleanupFn = () => void;
