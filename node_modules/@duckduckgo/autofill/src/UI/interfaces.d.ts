/**
 * Aliases to prevent 'import' everywhere
 */
type LocalTooltip = import("../UI/Tooltip.js").Tooltip;
type Form = import("../Form/Form").Form;
type Device = import("../DeviceInterface/InterfacePrototype").default;

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

interface AttachArgs {
  form: Form;
  input: HTMLInputElement;
  getPosition: PosFn;
  click: { x: number; y: number; } | null;
  topContextData: TopContextData
  device: Device
}

/**
 * The TooltipInterface is an abstraction over the concept
 * of 'attaching'. On ios/android this may result in nothing more than showing
 * an overlay, but on other platforms it may... todo(Shane): Description
 */
interface TooltipInterface {
  getActiveTooltip?(): LocalTooltip | null;
  setActiveTooltip?(tooltip: LocalTooltip);
  addListener?(cb: ()=>void);
  removeTooltip?();
  attach(args: AttachArgs): void;

  /**
   * @deprecated use 'attach' only
   */
  createTooltip?(pos: PosFn, topContextData: TopContextData): LocalTooltip;
  setDevice?(device: Device);
}

interface WebTooltipHandler {
  tooltipWrapperClass(): string;
  tooltipStyles(): string;
  tooltipPositionClass(top: number, left: number): string
  removeTooltip();
  setupSizeListener(cb: ()=>void);
  setSize(cb: ()=>void);
}
